import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "@/features/auth/AuthShell";
import { getGetCurrentUserQueryKey, useLogin } from "@/lib/api/generated/auth/auth";
import { translateApiError } from "@/lib/errors";

const FIELD_CLASS =
  "h-11 rounded-field border-none bg-field px-3.5 text-sm font-semibold shadow-none focus-visible:ring-2 focus-visible:ring-mint/40";

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const loginMutation = useLogin();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    loginMutation.mutate(
      { data: { email, password } },
      {
        onSuccess: (user) => {
          queryClient.setQueryData(getGetCurrentUserQueryKey(), user);
          navigate("/", { replace: true });
        },
        onError: (error) => {
          toast.error(translateApiError(t, error));
        },
      },
    );
  }

  return (
    <AuthShell>
      <div className="flex w-full flex-col gap-4 rounded-modal bg-card p-8 shadow-[var(--shadow-modal)]">
        <div className="flex items-center gap-2.5">
          <span className="flex size-8 items-center justify-center rounded-[11px] bg-mint text-sm font-extrabold text-mint-ink">
            M
          </span>
          <span className="text-sm font-extrabold tracking-[-0.01em] text-ink-primary">{t("app.name")}</span>
        </div>

        <div className="flex flex-col gap-1">
          <h1 className="text-[22px] font-extrabold tracking-[-0.025em] text-ink-primary">
            {t("auth.login")}
          </h1>
          <p className="text-xs text-ink-tertiary">{t("auth.loginSubtitle")}</p>
        </div>

        <form className="flex flex-col gap-3.5" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="login-email" className="text-[11px] font-bold text-ink-label">
              {t("auth.email")}
            </Label>
            <Input
              id="login-email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className={FIELD_CLASS}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="login-password" className="text-[11px] font-bold text-ink-label">
                {t("auth.password")}
              </Label>
              <button
                type="button"
                onClick={() => setShowPassword((value) => !value)}
                className="text-[11px] font-bold text-link hover:text-link-hover"
              >
                {showPassword ? t("auth.hidePassword") : t("auth.showPassword")}
              </button>
            </div>
            <Input
              id="login-password"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              required
              minLength={8}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={FIELD_CLASS}
            />
          </div>
          <Button type="submit" disabled={loginMutation.isPending} className="mt-1 w-full">
            {loginMutation.isPending ? t("auth.submitting") : t("auth.login")}
          </Button>
        </form>

        <p className="text-center text-xs text-ink-tertiary">
          {t("auth.noAccountPrompt")}{" "}
          <Link to="/register" className="font-bold text-link hover:text-link-hover">
            {t("auth.register")}
          </Link>
        </p>
      </div>
    </AuthShell>
  );
}
