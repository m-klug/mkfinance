import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import ptBR from "./locales/pt-BR/common.json";

// pt-BR is the only published locale in the MVP; the structure is i18n-ready
// from day one (PLANEJAMENTO §3.3): no hardcoded strings anywhere in the UI.
i18n.use(initReactI18next).init({
  resources: {
    "pt-BR": { common: ptBR },
  },
  lng: "pt-BR",
  fallbackLng: "pt-BR",
  defaultNS: "common",
  interpolation: {
    escapeValue: false,
  },
});

export default i18n;
