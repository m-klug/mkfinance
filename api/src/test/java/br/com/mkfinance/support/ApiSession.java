package br.com.mkfinance.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

/**
 * Drives the real API exactly like a browser-based SPA would: keeps whatever cookies the server
 * sets (XSRF-TOKEN, MK_FINANCE_SESSION) and replays them, echoing XSRF-TOKEN in the
 * X-XSRF-TOKEN header on state-changing requests — exercising the real CSRF + session wiring
 * end to end instead of bypassing it with test-only shortcuts.
 */
public class ApiSession {

    private final TestRestTemplate restTemplate;
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public ApiSession(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** Performs a harmless GET so the server issues the XSRF-TOKEN cookie before any POST. */
    public void primeCsrf() {
        get("/auth/me", String.class);
    }

    public <T> ResponseEntity<T> get(String path, Class<T> type) {
        return exchange(path, HttpMethod.GET, null, type);
    }

    public <T> ResponseEntity<T> post(String path, Object body, Class<T> type) {
        return exchange(path, HttpMethod.POST, body, type);
    }

    public <T> ResponseEntity<T> patch(String path, Object body, Class<T> type) {
        return exchange(path, HttpMethod.PATCH, body, type);
    }

    public <T> ResponseEntity<T> put(String path, Object body, Class<T> type) {
        return exchange(path, HttpMethod.PUT, body, type);
    }

    public <T> ResponseEntity<T> delete(String path, Class<T> type) {
        return exchange(path, HttpMethod.DELETE, null, type);
    }

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        if (!cookies.isEmpty()) {
            headers.add(HttpHeaders.COOKIE, cookieHeader());
        }
        if (method != HttpMethod.GET && cookies.containsKey("XSRF-TOKEN")) {
            headers.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }
        if (body instanceof MultiValueMap) {
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        } else if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<T> response = restTemplate.exchange(path, method, entity, type);
        captureCookies(response);
        return response;
    }

    private void captureCookies(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return;
        }
        for (String setCookie : setCookies) {
            String pair = setCookie.split(";", 2)[0];
            int idx = pair.indexOf('=');
            if (idx > 0) {
                cookies.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
    }

    private String cookieHeader() {
        return cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }
}
