-- Permissões do sistema (verificadas no servidor com @PreAuthorize) e perfil administrador.
INSERT INTO permissao (codigo, descricao) VALUES
    ('CATALOGO_GERENCIAR',   'Gerenciar categorias, insumos, produtos, grupos, opções e adicionais'),
    ('PEDIDO_VER',           'Visualizar pedidos'),
    ('PEDIDO_GERENCIAR',     'Alterar estado de pedidos e concluir entregas/retiradas'),
    ('CANCELAMENTO_DECIDIR', 'Cancelar pedidos e decidir solicitações de cancelamento'),
    ('PAGAMENTO_CONFIRMAR',  'Confirmar recebimento de pagamentos'),
    ('MESA_GERENCIAR',       'Gerenciar contas de mesa e fechamento'),
    ('CONFIG_GERENCIAR',     'Gerenciar taxa e áreas de entrega'),
    ('RELATORIO_VER',        'Visualizar relatórios operacionais'),
    ('FUNCIONARIO_GERENCIAR','Gerenciar funcionários'),
    ('PERFIL_GERENCIAR',     'Gerenciar perfis e permissões');

INSERT INTO perfil (nome, descricao) VALUES
    ('ADMINISTRADOR', 'Acesso total ao sistema'),
    ('ATENDENTE',     'Operação de pedidos, mesas e pagamentos'),
    ('COZINHA',       'Acompanha e atualiza o preparo dos pedidos');

INSERT INTO perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id FROM perfil p CROSS JOIN permissao pe WHERE p.nome = 'ADMINISTRADOR';

INSERT INTO perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id FROM perfil p JOIN permissao pe
    ON pe.codigo IN ('PEDIDO_VER', 'PEDIDO_GERENCIAR', 'PAGAMENTO_CONFIRMAR', 'MESA_GERENCIAR', 'CANCELAMENTO_DECIDIR')
WHERE p.nome = 'ATENDENTE';

INSERT INTO perfil_permissao (perfil_id, permissao_id)
SELECT p.id, pe.id FROM perfil p JOIN permissao pe
    ON pe.codigo IN ('PEDIDO_VER', 'PEDIDO_GERENCIAR')
WHERE p.nome = 'COZINHA';

-- Taxa de entrega fixa (configurável pela pizzaria).
INSERT INTO configuracao_loja (chave, valor) VALUES ('TAXA_ENTREGA', '0.00');
