-- A abertura do caixa não libera automaticamente os pedidos feitos pela loja pública.
-- A opção é definida pelo funcionário ao iniciar a sessão e permanece vinculada a ela.
ALTER TABLE caixa
    ADD COLUMN vendas_online BOOLEAN NOT NULL DEFAULT FALSE AFTER aberto_por;
