import { MemberRole, type CurrentUser } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { MOCK_SESSION_KEY } from "@/mocks/handlers";

// Matches the shape the MSW auth handlers (src/mocks/handlers.ts) read from
// localStorage. Tests seed this before render to start already logged in.
export const fakeUser: CurrentUser = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Maria Teste",
  email: "maria@example.com",
  locale: "pt-BR",
  workspaces: [
    { id: "22222222-2222-2222-2222-222222222222", name: "Espaço de Maria", role: MemberRole.OWNER },
  ],
};

export function seedAuthenticatedSession(user: CurrentUser = fakeUser) {
  localStorage.setItem(MOCK_SESSION_KEY, JSON.stringify(user));
}
