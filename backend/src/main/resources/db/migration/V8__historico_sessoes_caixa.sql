-- Cada pedido pertence à sessão que estava aberta quando foi confirmado.
-- Sessões encerradas podem ser retomadas pelo histórico, mantendo o mesmo registro.
ALTER TABLE caixa
    ADD COLUMN quantidade_reaberturas INT UNSIGNED NOT NULL DEFAULT 0 AFTER pedidos_cancelados,
    ADD COLUMN ultima_reabertura_em DATETIME NULL AFTER quantidade_reaberturas,
    ADD COLUMN ultima_reabertura_por BIGINT UNSIGNED NULL AFTER ultima_reabertura_em,
    ADD CONSTRAINT fk_caixa_ultima_reabertura_por FOREIGN KEY (ultima_reabertura_por) REFERENCES funcionario(id);

ALTER TABLE pedido
    ADD COLUMN caixa_id BIGINT UNSIGNED NULL AFTER conta_mesa_id,
    ADD COLUMN ultima_edicao_caixa_em DATETIME NULL AFTER atualizado_em,
    ADD COLUMN ultima_edicao_caixa_por BIGINT UNSIGNED NULL AFTER ultima_edicao_caixa_em,
    ADD COLUMN justificativa_ultima_edicao VARCHAR(500) NULL AFTER ultima_edicao_caixa_por,
    ADD KEY idx_pedido_caixa (caixa_id),
    ADD CONSTRAINT fk_pedido_caixa FOREIGN KEY (caixa_id) REFERENCES caixa(id),
    ADD CONSTRAINT fk_pedido_ultima_edicao_caixa_por FOREIGN KEY (ultima_edicao_caixa_por) REFERENCES funcionario(id);

-- Vincula dados anteriores à sessão cujo intervalo contém a criação do pedido.
UPDATE pedido p
SET p.caixa_id = (
    SELECT c.id FROM caixa c
    WHERE p.criado_em >= c.aberto_em AND (c.fechado_em IS NULL OR p.criado_em <= c.fechado_em)
    ORDER BY c.aberto_em DESC LIMIT 1
)
WHERE p.caixa_id IS NULL;

CREATE TABLE historico_edicao_venda (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT UNSIGNED NOT NULL,
    funcionario_id BIGINT UNSIGNED NOT NULL,
    nome_anterior VARCHAR(150) NULL,
    nome_novo VARCHAR(150) NOT NULL,
    cpf_alterado BOOLEAN NOT NULL DEFAULT FALSE,
    observacao_anterior VARCHAR(1000) NULL,
    observacao_nova VARCHAR(1000) NULL,
    metodo_anterior VARCHAR(15) NULL,
    metodo_novo VARCHAR(15) NULL,
    justificativa VARCHAR(500) NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_historico_edicao_venda_pedido (pedido_id, criado_em),
    CONSTRAINT fk_historico_edicao_venda_pedido FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    CONSTRAINT fk_historico_edicao_venda_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario(id)
) ENGINE=InnoDB;
