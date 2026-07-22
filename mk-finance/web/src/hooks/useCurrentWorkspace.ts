import { useGetCurrentUser } from "@/lib/api/generated/auth/auth";

// Every data endpoint is scoped by workspace (PLANEJAMENTO §5 R8), so every
// feature screen needs the active workspace id. The MVP has exactly one
// workspace per user (the personal one created at registration), so "active"
// is simply the first of the list — when workspace switching lands, only this
// hook changes and no screen has to be touched.
export function useCurrentWorkspaceId(): string | undefined {
  const { data } = useGetCurrentUser();
  return data?.workspaces[0]?.id;
}
