// Custom fetch mutator used by every generated API hook (see orval.config.ts).
// Orval calls it with a single request-config object. Sends the session cookie
// and unwraps JSON; non-2xx responses throw ApiError carrying the
// machine-readable error code the UI translates (PLANEJAMENTO §3.3).

export interface ApiErrorBody {
  code: string;
  params?: Record<string, unknown>;
  fieldErrors?: { field: string; code: string }[];
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody,
  ) {
    super(body.code);
    this.name = "ApiError";
  }
}

export interface ApiRequestConfig {
  url: string;
  method: string;
  params?: Record<string, unknown>;
  data?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

// The backend runs under the servlet context-path `/api` (application.yaml) and, in dev, is
// reached through the Vite proxy for `/api` (vite.config.ts). The generated client emits
// context-relative URLs (e.g. `/auth/register`), so we prefix the API base here. Overridable per
// environment via VITE_API_BASE_URL; defaults to `/api` for both the Vite proxy (dev) and Caddy
// (prod), which both route `/api/*` to the backend.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

/**
 * Reads a non-httpOnly cookie set by the backend. Used for the CSRF double-submit flow: the server
 * issues a readable `XSRF-TOKEN` cookie (CookieCsrfTokenRepository, path `/`) and expects it echoed
 * verbatim in the `X-XSRF-TOKEN` header on state-changing requests.
 */
function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") {
    return undefined;
  }
  const prefix = `${name}=`;
  for (const part of document.cookie.split(";")) {
    const cookie = part.trim();
    if (cookie.startsWith(prefix)) {
      return decodeURIComponent(cookie.slice(prefix.length));
    }
  }
  return undefined;
}

function buildQuery(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    // Array params are `style: form, explode: true` in the contract — repeated keys, not one
    // comma-joined value. String(array) would produce the latter, which happens to bind in Spring
    // but silently swallows any value containing a comma.
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item !== undefined && item !== null) {
          search.append(key, String(item));
        }
      }
      continue;
    }
    search.append(key, String(value));
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}

export async function apiFetch<T>(config: ApiRequestConfig): Promise<T> {
  const { url, method, params, data, headers, signal } = config;
  const fullUrl = `${API_BASE_URL}${url}${params ? buildQuery(params) : ""}`;

  // Echo the CSRF token on state-changing requests (double-submit cookie pattern). Read fresh per
  // request so token rotation after login (Spring's CsrfAuthenticationStrategy) is picked up. When
  // MSW serves mocks or in tests, the cookie is absent and the header is simply omitted — mocks
  // don't enforce CSRF.
  const finalHeaders: Record<string, string> = { ...headers };
  if (MUTATING_METHODS.has(method.toUpperCase())) {
    const token = readCookie(CSRF_COOKIE);
    if (token !== undefined) {
      finalHeaders[CSRF_HEADER] = token;
    }
  }

  // FormData goes out as-is: the browser sets the multipart Content-Type (with its boundary) itself.
  const isForm = typeof FormData !== "undefined" && data instanceof FormData;
  if (isForm) {
    // The generated client labels multipart calls with a bare Content-Type, which would leave the
    // browser unable to add the boundary parameter. Drop it so fetch derives the right header.
    for (const name of Object.keys(finalHeaders)) {
      if (name.toLowerCase() === "content-type") delete finalHeaders[name];
    }
  }

  const init: RequestInit = {
    method,
    credentials: "include",
    headers: finalHeaders,
    body: data === undefined ? undefined : isForm ? (data as FormData) : JSON.stringify(data),
    signal,
  };

  let response: Response;
  try {
    response = await fetch(fullUrl, init);
  } catch (error) {
    // Vitest's jsdom test environment ships its own AbortController/
    // AbortSignal (unlike fetch/Request, which stay Node's native
    // implementation), so a signal minted by TanStack Query fails Node
    // fetch's realm check with this exact TypeError. Never happens in a
    // real browser (single realm) — in tests we just retry without the
    // signal rather than making every test special-case cancellation.
    const isJsdomAbortSignalMismatch =
      signal !== undefined && error instanceof TypeError && error.message.includes("AbortSignal");
    if (!isJsdomAbortSignalMismatch) {
      throw error;
    }
    response = await fetch(fullUrl, { ...init, signal: undefined });
  }

  if (!response.ok) {
    let body: ApiErrorBody = { code: "UNKNOWN_ERROR" };
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      // non-JSON error body — keep UNKNOWN_ERROR
    }
    throw new ApiError(response.status, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("text/csv")) {
    return (await response.text()) as T;
  }
  return (await response.json()) as T;
}

export default apiFetch;
