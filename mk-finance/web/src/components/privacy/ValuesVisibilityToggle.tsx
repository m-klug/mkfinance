import { Eye, EyeOff } from "lucide-react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { useValuesVisibility } from "@/hooks/useValuesVisibility";

export function ValuesVisibilityToggle() {
  const { t } = useTranslation();
  const { hidden, toggleHidden } = useValuesVisibility();

  return (
    <Button
      variant="outline"
      size="icon"
      aria-pressed={hidden}
      aria-label={t(hidden ? "privacy.show" : "privacy.hide")}
      title={t(hidden ? "privacy.show" : "privacy.hide")}
      onClick={toggleHidden}
    >
      {hidden ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
    </Button>
  );
}
