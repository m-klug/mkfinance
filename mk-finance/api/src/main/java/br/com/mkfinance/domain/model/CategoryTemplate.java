package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Read-only reference data from {@code category_template} (V2__seed.sql, pt-BR). Copied into a
 * new workspace's {@code category} table by {@code WorkspaceDefaultsSeeder} — never modified by
 * the application.
 */
@Entity
@Table(name = "category_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTemplate {

    @Id
    private Short id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CategoryType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Pillar pillar;

    @Column(length = 40)
    private String icon;

    @Column(length = 7)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;
}
