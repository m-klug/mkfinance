import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeToggle } from "./ThemeToggle";
import { useThemeStore } from "@/hooks/useTheme";

describe("ThemeToggle", () => {
  beforeEach(() => {
    document.documentElement.classList.remove("dark");
    useThemeStore.setState({ theme: "light" });
    localStorage.removeItem("mk-finance-theme");
  });

  it("switches to dark mode and persists the choice", async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);

    expect(document.documentElement.classList.contains("dark")).toBe(false);

    await user.click(screen.getByRole("button", { name: "Alternar tema" }));
    await user.click(await screen.findByText("Escuro"));

    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(useThemeStore.getState().theme).toBe("dark");

    const stored = localStorage.getItem("mk-finance-theme");
    expect(stored).not.toBeNull();
    expect(JSON.parse(stored ?? "{}").state.theme).toBe("dark");
  });

  it("switches back to light mode", async () => {
    const user = userEvent.setup();
    useThemeStore.getState().setTheme("dark");
    render(<ThemeToggle />);

    await user.click(screen.getByRole("button", { name: "Alternar tema" }));
    await user.click(await screen.findByText("Claro"));

    expect(document.documentElement.classList.contains("dark")).toBe(false);
    expect(useThemeStore.getState().theme).toBe("light");
  });
});
