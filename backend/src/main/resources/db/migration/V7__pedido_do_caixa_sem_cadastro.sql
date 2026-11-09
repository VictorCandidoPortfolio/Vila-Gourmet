-- Pedidos lançados pelo funcionário no caixa só exigem o nome do cliente: sem conta de cliente e sem CPF.
-- (Na loja do cliente continuam obrigatórios: a validação está no serviço.)
ALTER TABLE pedido DROP CONSTRAINT chk_pedido_entrega_dados;
ALTER TABLE pedido ADD CONSTRAINT chk_pedido_entrega_dados CHECK (
    modalidade <> 'ENTREGA'
    OR (endereco_cep IS NOT NULL AND endereco_logradouro IS NOT NULL
        AND endereco_numero IS NOT NULL AND endereco_bairro IS NOT NULL
        AND endereco_cidade IS NOT NULL AND endereco_estado IS NOT NULL));
ALTER TABLE pedido MODIFY cpf_responsavel CHAR(11) NULL;
ALTER TABLE conta_mesa MODIFY cpf_responsavel CHAR(11) NULL;
