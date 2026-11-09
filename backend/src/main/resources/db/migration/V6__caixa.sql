-- Caixa (frente de caixa): a pizzaria só vende enquanto houver um caixa aberto.
-- No máximo um caixa aberto por vez (aberto_chave só tem valor enquanto fechado_em é nulo).
CREATE TABLE caixa (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    aberto_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aberto_por BIGINT UNSIGNED NOT NULL,
    fechado_em DATETIME NULL,
    fechado_por BIGINT UNSIGNED NULL,
    pedidos_cancelados INT UNSIGNED NOT NULL DEFAULT 0,
    aberto_chave TINYINT GENERATED ALWAYS AS (IF(fechado_em IS NULL, 1, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_caixa_um_aberto (aberto_chave),
    KEY idx_caixa_aberto_em (aberto_em),
    CONSTRAINT fk_caixa_aberto_por FOREIGN KEY (aberto_por) REFERENCES funcionario(id),
    CONSTRAINT fk_caixa_fechado_por FOREIGN KEY (fechado_por) REFERENCES funcionario(id)
) ENGINE=InnoDB;

-- Permissão para abrir/fechar o caixa e lançar pedidos nele; administradores e atendentes já recebem.
INSERT INTO permissao (codigo, descricao) VALUES
    ('CAIXA_OPERAR', 'Abrir e fechar o caixa e lançar pedidos na frente de caixa');

INSERT INTO perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id FROM perfil p JOIN permissao pe ON pe.codigo = 'CAIXA_OPERAR'
WHERE p.nome IN ('ADMINISTRADOR', 'ATENDENTE');
