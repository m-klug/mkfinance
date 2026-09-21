import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AuthShell } from "@/features/auth/AuthShell";
import { getGetCurrentUserQueryKey, useRegister } from "@/lib/api/generated/auth/auth";
import { translateApiError } from "@/lib/errors";

const FIELD_CLASS =
  "h-11 rounded-field border-none bg-field px-3.5 text-sm font-semibold shadow-none focus-visible:ring-2 focus-visible:ring-mint/40";

export default function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const registerMutation = useRegister();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    registerMutation.mutate(
      { data: { name, email, password } },
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
            {t("auth.register")}
          </h1>
          <p className="text-xs text-ink-tertiary">{t("auth.registerSubtitle")}</p>
        </div>

        <form className="flex flex-col gap-3.5" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="register-name" className="text-[11px] font-bold text-ink-label">
              {t("auth.name")}
            </Label>
            <Input
              id="register-name"
              type="text"
              autoComplete="name"
              required
              maxLength={120}
              value={name}
              onChange={(event) => setName(event.target.value)}
              className={FIELD_CLASS}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="register-email" className="text-[11px] font-bold text-ink-label">
              {t("auth.email")}
            </Label>
            <Input
              id="register-email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className={FIELD_CLASS}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="register-password" className="text-[11px] font-bold text-ink-label">
              {t("auth.password")}
            </Label>
            <Input
              id="register-password"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={FIELD_CLASS}
            />
            <span className="text-[11px] text-ink-label">{t("auth.passwordMinLength")}</span>
          </div>
          <Button
            type="submit"
            disabled={registerMutation.isPending}
            className="mt-1 w-full max-sm:h-11"
          >
            {registerMutation.isPending ? t("auth.submitting") : t("auth.register")}
          </Button>
        </form>

        <p className="text-center text-xs text-ink-tertiary">
          {t("auth.hasAccountPrompt")}{" "}
          <Link to="/login" className="font-bold text-link hover:text-link-hover">
            {t("auth.login")}
          </Link>
        </p>
      </div>
    </AuthShell>
  );
}
