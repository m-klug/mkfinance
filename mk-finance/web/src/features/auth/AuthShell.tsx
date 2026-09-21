import type { ReactNode } from "react";

interface AuthShellProps {
  children: ReactNode;
}

/**
 * Login/Register shell (design_handoff_mk_finance/README.md screen 13):
 * app floor with a single centered card — logo, title, fields all live
 * INSIDE that card. No marketing panel, no pillar illustration outside it
 * — "Não há painel de marketing nem card de cadastro."
 */
export function AuthShell({ children }: AuthShellProps) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-page-bg p-4">
      <div className="w-full max-w-[400px]">{children}</div>
    </div>
  );
}
