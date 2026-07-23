import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";
import path from "node:path";

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    // Installable PWA (INT-1): generates the manifest + a Workbox service worker
    // that precaches the app shell. `autoUpdate` swaps in a new SW as soon as it
    // is available (no update prompt UI to maintain). The API (/api) is never
    // cached — only static assets — so data always comes from the backend.
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["favicon.svg", "apple-touch-icon.png"],
      workbox: {
        navigateFallbackDenylist: [/^\/api/],
        globPatterns: ["**/*.{js,css,html,svg,png,woff2}"],
        // MSW's worker ships in public/ for the dev mock mode; it must never be
        // precached or served by the PWA runtime.
        globIgnores: ["**/mockServiceWorker.js"],
      },
      manifest: {
        name: "MK Finance",
        short_name: "MK Finance",
        description: "Controle financeiro pessoal pelo método dos 6 pilares.",
        lang: "pt-BR",
        theme_color: "#16A34A",
        background_color: "#ffffff",
        display: "standalone",
        start_url: "/",
        scope: "/",
        icons: [
          { src: "pwa-192x192.png", sizes: "192x192", type: "image/png" },
          { src: "pwa-512x512.png", sizes: "512x512", type: "image/png" },
          {
            src: "maskable-512x512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // Real API during integrated development (INT-1). While VITE_API_MOCK=true,
      // MSW intercepts requests before they reach this proxy.
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.ts",
    // Vitest owns the unit/component tests under src/; the Playwright E2E specs
    // in e2e/ (also *.spec.ts) run under a different runner and must be excluded
    // here, or Vitest would try to execute them in jsdom.
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    exclude: ["e2e/**", "node_modules/**", "dist/**"],
  },
});
