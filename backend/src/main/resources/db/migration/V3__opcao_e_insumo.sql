-- A opção de um grupo passa a ser sempre um insumo cadastrado (o nome da opção vem do insumo).
-- Opções antigas sem insumo ganham um insumo de mesmo nome para não perder dados.
INSERT INTO insumo (nome, disponivel)
SELECT DISTINCT o.nome, TRUE FROM opcao o
WHERE o.insumo_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM insumo i WHERE i.nome = o.nome);

UPDATE opcao o
JOIN (SELECT nome, MIN(id) AS id FROM insumo GROUP BY nome) i ON i.nome = o.nome
SET o.insumo_id = i.id
WHERE o.insumo_id IS NULL;

ALTER TABLE opcao MODIFY insumo_id BIGINT UNSIGNED NOT NULL;
