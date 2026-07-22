-- Global category templates (pt-BR). Copied into each new workspace by the
-- application (WorkspaceDefaultsSeeder, BE-2) together with the fixed pillar
-- allocation 30/10/20/10/25/5. Templates are data, not i18n resources
-- (PLANEJAMENTO §3.3): they become user-editable categories at workspace creation.

CREATE TABLE category_template (
    id         SMALLINT PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    type       VARCHAR(8)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    pillar     VARCHAR(20)
        CHECK (pillar IN ('FIXED_COSTS', 'COMFORT', 'GOALS', 'PLEASURES', 'FINANCIAL_FREEDOM', 'KNOWLEDGE')),
    icon       VARCHAR(40),
    color      VARCHAR(7),
    sort_order SMALLINT    NOT NULL,
    CONSTRAINT ck_category_template_pillar CHECK ((type = 'EXPENSE') = (pillar IS NOT NULL))
);

INSERT INTO category_template (id, name, type, pillar, icon, color, sort_order) VALUES
    -- FIXED_COSTS (Custos Fixos)
    (1,  'Moradia',             'EXPENSE', 'FIXED_COSTS',       'home',           '#0EA5E9', 1),
    (2,  'Mercado',             'EXPENSE', 'FIXED_COSTS',       'shopping-cart',  '#0EA5E9', 2),
    (3,  'Contas da casa',      'EXPENSE', 'FIXED_COSTS',       'plug-zap',       '#0EA5E9', 3),
    (4,  'Transporte',          'EXPENSE', 'FIXED_COSTS',       'car',            '#0EA5E9', 4),
    (5,  'Saúde',               'EXPENSE', 'FIXED_COSTS',       'heart-pulse',    '#0EA5E9', 5),
    (6,  'Impostos e taxas',    'EXPENSE', 'FIXED_COSTS',       'landmark',       '#0EA5E9', 6),
    -- COMFORT (Conforto)
    (10, 'Academia',            'EXPENSE', 'COMFORT',           'dumbbell',       '#8B5CF6', 1),
    (11, 'Assinaturas',         'EXPENSE', 'COMFORT',           'repeat',         '#8B5CF6', 2),
    (12, 'Cuidados pessoais',   'EXPENSE', 'COMFORT',           'sparkles',       '#8B5CF6', 3),
    -- GOALS (Metas)
    (20, 'Gastos de metas',     'EXPENSE', 'GOALS',             'target',         '#F59E0B', 1),
    -- PLEASURES (Prazeres)
    (30, 'Restaurantes e bares','EXPENSE', 'PLEASURES',         'utensils',       '#EC4899', 1),
    (31, 'Delivery',            'EXPENSE', 'PLEASURES',         'bike',           '#EC4899', 2),
    (32, 'Lazer',               'EXPENSE', 'PLEASURES',         'gamepad-2',      '#EC4899', 3),
    (33, 'Viagens',             'EXPENSE', 'PLEASURES',         'plane',          '#EC4899', 4),
    (34, 'Compras pessoais',    'EXPENSE', 'PLEASURES',         'shopping-bag',   '#EC4899', 5),
    -- FINANCIAL_FREEDOM (Liberdade Financeira)
    (40, 'Liberdade financeira','EXPENSE', 'FINANCIAL_FREEDOM', 'trending-up',    '#16A34A', 1),
    -- KNOWLEDGE (Conhecimento)
    (50, 'Livros',              'EXPENSE', 'KNOWLEDGE',         'book-open',      '#F97316', 1),
    (51, 'Cursos e estudos',    'EXPENSE', 'KNOWLEDGE',         'graduation-cap', '#F97316', 2),
    -- INCOME (sem pilar)
    (60, 'Salário',             'INCOME',  NULL,                'banknote',       '#16A34A', 1),
    (61, 'Renda extra',         'INCOME',  NULL,                'briefcase',      '#16A34A', 2),
    (62, 'Rendimentos',         'INCOME',  NULL,                'percent',        '#16A34A', 3),
    (63, 'Outras receitas',     'INCOME',  NULL,                'coins',          '#16A34A', 4);
