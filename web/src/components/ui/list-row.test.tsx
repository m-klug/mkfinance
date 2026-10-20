import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";

import { ListRow } from "./list-row";

describe("ListRow", () => {
  it("trunca o título e não deixa o valor comprimir (regressão do squeeze)", () => {
    render(
      <ListRow
        title="Nome bem longo que deveria truncar ao invés de empurrar o valor pra fora"
        value="R$ 1.234,56"
      />,
    );

    const title = screen.getByText(/Nome bem longo/);
    expect(title.className).toContain("truncate");
    expect(title.parentElement?.className).toContain("min-w-0");
    expect(title.parentElement?.className).toContain("flex-1");

    const value = screen.getByText("R$ 1.234,56");
    expect(value.className).toContain("shrink-0");
  });

  it("nunca aninha o trailing dentro da área clicável (href)", () => {
    render(
      <MemoryRouter>
        <ListRow title="Conta corrente" href="/accounts/1" trailing={<button>Excluir</button>} />
      </MemoryRouter>,
    );

    const link = screen.getByRole("link");
    const trailing = screen.getByRole("button", { name: "Excluir" });
    expect(link.contains(trailing)).toBe(false);
  });

  it("nunca aninha o trailing dentro da área clicável (onClick)", () => {
    const onClick = vi.fn();
    render(
      <ListRow title="Conta corrente" onClick={onClick} trailing={<button>Excluir</button>} />,
    );

    const row = screen.getByRole("button", { name: "Conta corrente" });
    const trailing = screen.getByRole("button", { name: "Excluir" });
    expect(row.contains(trailing)).toBe(false);
  });

  it("sem href/onClick não renderiza elemento interativo", () => {
    render(<ListRow title="Saldo" value="R$ 10,00" />);

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Saldo" })).not.toBeInTheDocument();
  });
});
