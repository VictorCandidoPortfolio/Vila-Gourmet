-- Áreas de entrega passam a poder ser ligadas a um município do IBGE (código + UF).
-- Linhas antigas (cadastro manual) continuam válidas, sem código IBGE.
-- A unicidade passa a considerar o município: cidades de mesmo nome em UFs diferentes (ex.: Santa Luzia)
-- podem ter um mesmo bairro ("Centro"). chave_cidade usa 0 quando não há código, para a regra valer também no manual.
ALTER TABLE area_entrega
    ADD COLUMN cidade_ibge_id INT UNSIGNED NULL,
    ADD COLUMN uf CHAR(2) NULL,
    ADD COLUMN origem VARCHAR(12) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN chave_cidade INT UNSIGNED GENERATED ALWAYS AS (IFNULL(cidade_ibge_id, 0)) STORED,
    ADD CONSTRAINT ck_area_origem CHECK (origem IN ('MANUAL', 'IBGE_OSM')),
    DROP INDEX uq_area_bairro_cidade,
    ADD UNIQUE KEY uq_area_bairro_cidade (bairro, cidade, chave_cidade),
    ADD KEY ix_area_cidade_ibge (cidade_ibge_id);
