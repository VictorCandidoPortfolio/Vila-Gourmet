-- Vila Gourmet — esquema inicial (MySQL 8.0.16+)
-- Evolução do modelo lógico v0.1. Melhorias aplicadas:
--  * remoção do CREATE DATABASE/USE (migração Flyway roda no schema configurado);
--  * desativação lógica (ativo/disponivel) no lugar de exclusão física;
--  * produto com status de publicação (RASCUNHO/PUBLICADO);
--  * semântica clara de participa_preco_base (sem o CHECK que forçava TRUE em FIXO);
--  * areas_entrega e configuracao_loja (taxa fixa); pizza_salva;
--  * snapshots completos (modalidade de cobrança, participação do preço-base);
--  * CHECKs de estado, de totais e de pagamento confirmado;
--  * unicidade parcial via colunas geradas (endereço padrão, pagamento confirmado,
--    fechamento pendente, código de confirmação ativo);
--  * códigos de rastreamento/confirmação com HMAC determinístico, expiração e tentativas;
--  * chave de idempotência em pedido; histórico de conta de mesa.

-- =========================================================
-- 1. CONTAS, CLIENTES, FUNCIONÁRIOS E PERMISSÕES
-- =========================================================

CREATE TABLE conta_cliente (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_conta_cliente_email (email)
) ENGINE=InnoDB;

CREATE TABLE cliente (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conta_cliente_id BIGINT UNSIGNED NOT NULL,
    nome VARCHAR(150) NOT NULL,
    cpf CHAR(11) NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cliente_conta (conta_cliente_id),
    KEY idx_cliente_cpf (cpf),
    CONSTRAINT fk_cliente_conta FOREIGN KEY (conta_cliente_id) REFERENCES conta_cliente(id)
) ENGINE=InnoDB;

CREATE TABLE endereco (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cliente_id BIGINT UNSIGNED NOT NULL,
    identificacao VARCHAR(80) NULL,
    cep CHAR(8) NOT NULL,
    logradouro VARCHAR(200) NOT NULL,
    numero VARCHAR(20) NOT NULL,
    complemento VARCHAR(100) NULL,
    bairro VARCHAR(100) NOT NULL,
    cidade VARCHAR(100) NOT NULL,
    estado CHAR(2) NOT NULL,
    padrao BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- garante no máximo um endereço padrão ativo por cliente
    padrao_chave BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN padrao = 1 AND ativo = 1 THEN cliente_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    KEY idx_endereco_cliente (cliente_id),
    UNIQUE KEY uq_endereco_padrao (padrao_chave),
    CONSTRAINT fk_endereco_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id)
) ENGINE=InnoDB;

CREATE TABLE conta_funcionario (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_conta_funcionario_email (email)
) ENGINE=InnoDB;

CREATE TABLE funcionario (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conta_funcionario_id BIGINT UNSIGNED NOT NULL,
    nome VARCHAR(150) NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_funcionario_conta (conta_funcionario_id),
    CONSTRAINT fk_funcionario_conta FOREIGN KEY (conta_funcionario_id) REFERENCES conta_funcionario(id)
) ENGINE=InnoDB;

CREATE TABLE perfil (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nome VARCHAR(80) NOT NULL,
    descricao VARCHAR(255) NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_perfil_nome (nome)
) ENGINE=InnoDB;

CREATE TABLE permissao (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(100) NOT NULL,
    descricao VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_permissao_codigo (codigo)
) ENGINE=InnoDB;

CREATE TABLE funcionario_perfil (
    funcionario_id BIGINT UNSIGNED NOT NULL,
    perfil_id BIGINT UNSIGNED NOT NULL,
    atribuido_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (funcionario_id, perfil_id),
    CONSTRAINT fk_funcionario_perfil_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario(id),
    CONSTRAINT fk_funcionario_perfil_perfil FOREIGN KEY (perfil_id) REFERENCES perfil(id)
) ENGINE=InnoDB;

CREATE TABLE perfil_permissao (
    perfil_id BIGINT UNSIGNED NOT NULL,
    permissao_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (perfil_id, permissao_id),
    CONSTRAINT fk_perfil_permissao_perfil FOREIGN KEY (perfil_id) REFERENCES perfil(id),
    CONSTRAINT fk_perfil_permissao_permissao FOREIGN KEY (permissao_id) REFERENCES permissao(id)
) ENGINE=InnoDB;

-- =========================================================
-- 2. CATÁLOGO, INSUMOS E PRECIFICAÇÃO
-- =========================================================

CREATE TABLE categoria (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nome VARCHAR(100) NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_categoria_nome (nome)
) ENGINE=InnoDB;

CREATE TABLE produto (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    categoria_id BIGINT UNSIGNED NOT NULL,
    tipo VARCHAR(15) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    descricao TEXT NULL,
    imagem_url VARCHAR(500) NULL,
    -- SIMPLES: preço de venda. COMPOSTO: preço-base (obrigatório na estratégia FIXO).
    preco_base DECIMAL(10,2) NULL,
    disponivel BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(12) NOT NULL DEFAULT 'RASCUNHO',
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_produto_categoria (categoria_id),
    KEY idx_produto_catalogo (status, disponivel),
    CONSTRAINT fk_produto_categoria FOREIGN KEY (categoria_id) REFERENCES categoria(id),
    CONSTRAINT chk_produto_tipo CHECK (tipo IN ('SIMPLES', 'COMPOSTO')),
    CONSTRAINT chk_produto_status CHECK (status IN ('RASCUNHO', 'PUBLICADO')),
    CONSTRAINT chk_produto_preco_base CHECK (preco_base IS NULL OR preco_base >= 0),
    CONSTRAINT chk_produto_simples_preco CHECK (tipo <> 'SIMPLES' OR preco_base IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE insumo (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nome VARCHAR(150) NOT NULL,
    descricao TEXT NULL,
    imagem_url VARCHAR(500) NULL,
    valor_referencia DECIMAL(10,2) NULL,
    disponivel BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_insumo_valor_referencia CHECK (valor_referencia IS NULL OR valor_referencia >= 0)
) ENGINE=InnoDB;

-- Somente para produtos COMPOSTOS. FIXO: preco_base é o preço principal.
-- participa_preco_base: nas estratégias SOMA/MEDIA/MAIOR_VALOR, indica se o preço-base entra na fórmula.
CREATE TABLE configuracao_precificacao (
    produto_id BIGINT UNSIGNED NOT NULL,
    estrategia VARCHAR(15) NOT NULL,
    participa_preco_base BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (produto_id),
    CONSTRAINT fk_precificacao_produto FOREIGN KEY (produto_id) REFERENCES produto(id),
    CONSTRAINT chk_precificacao_estrategia CHECK (estrategia IN ('FIXO', 'SOMA', 'MEDIA', 'MAIOR_VALOR'))
) ENGINE=InnoDB;

CREATE TABLE grupo_opcao (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    produto_id BIGINT UNSIGNED NOT NULL,
    nome VARCHAR(100) NOT NULL,
    minimo INT UNSIGNED NOT NULL DEFAULT 0,
    maximo INT UNSIGNED NOT NULL,
    modalidade_cobranca VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    KEY idx_grupo_produto (produto_id),
    CONSTRAINT fk_grupo_produto FOREIGN KEY (produto_id) REFERENCES produto(id),
    CONSTRAINT chk_grupo_limites CHECK (maximo >= minimo),
    CONSTRAINT chk_grupo_cobranca CHECK (modalidade_cobranca IN ('PRINCIPAL', 'SEPARADA', 'GRATUITA'))
) ENGINE=InnoDB;

CREATE TABLE opcao (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    grupo_opcao_id BIGINT UNSIGNED NOT NULL,
    insumo_id BIGINT UNSIGNED NULL,
    nome VARCHAR(150) NOT NULL,
    preco DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    minimo INT UNSIGNED NOT NULL DEFAULT 0,
    maximo INT UNSIGNED NOT NULL DEFAULT 1,
    disponivel BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_opcao_grupo (grupo_opcao_id),
    CONSTRAINT fk_opcao_grupo FOREIGN KEY (grupo_opcao_id) REFERENCES grupo_opcao(id),
    CONSTRAINT fk_opcao_insumo FOREIGN KEY (insumo_id) REFERENCES insumo(id),
    CONSTRAINT chk_opcao_preco CHECK (preco >= 0),
    CONSTRAINT chk_opcao_limites CHECK (maximo >= minimo)
) ENGINE=InnoDB;

CREATE TABLE adicional_simples (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    produto_id BIGINT UNSIGNED NOT NULL,
    nome VARCHAR(150) NOT NULL,
    preco DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    disponivel BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_adicional_produto (produto_id),
    CONSTRAINT fk_adicional_produto FOREIGN KEY (produto_id) REFERENCES produto(id),
    CONSTRAINT chk_adicional_preco CHECK (preco >= 0)
) ENGINE=InnoDB;

CREATE TABLE pizza_salva (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cliente_id BIGINT UNSIGNED NOT NULL,
    produto_id BIGINT UNSIGNED NOT NULL,
    nome VARCHAR(100) NOT NULL,
    selecao_json TEXT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pizza_salva_cliente (cliente_id),
    CONSTRAINT fk_pizza_salva_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id),
    CONSTRAINT fk_pizza_salva_produto FOREIGN KEY (produto_id) REFERENCES produto(id)
) ENGINE=InnoDB;

-- =========================================================
-- 3. CONFIGURAÇÃO DA LOJA E ÁREA DE ENTREGA
-- =========================================================

CREATE TABLE configuracao_loja (
    chave VARCHAR(60) NOT NULL,
    valor VARCHAR(255) NOT NULL,
    PRIMARY KEY (chave)
) ENGINE=InnoDB;

CREATE TABLE area_entrega (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bairro VARCHAR(100) NOT NULL,
    cidade VARCHAR(100) NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_area_bairro_cidade (bairro, cidade)
) ENGINE=InnoDB;

-- =========================================================
-- 4. CONTAS DE MESA
-- =========================================================

CREATE TABLE conta_mesa (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    codigo_conta VARCHAR(30) NOT NULL,
    nome_responsavel VARCHAR(150) NOT NULL,
    cpf_responsavel CHAR(11) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    aberta_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    encerrada_em DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_conta_mesa_codigo (codigo_conta),
    KEY idx_conta_mesa_cpf (cpf_responsavel),
    KEY idx_conta_mesa_estado (estado),
    CONSTRAINT chk_conta_mesa_estado CHECK (estado IN ('ABERTA', 'EM_FECHAMENTO', 'ENCERRADA'))
) ENGINE=InnoDB;

-- =========================================================
-- 5. PEDIDOS, ITENS E SNAPSHOTS HISTÓRICOS
-- =========================================================

CREATE TABLE pedido (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    numero_pedido VARCHAR(30) NOT NULL,
    chave_idempotencia VARCHAR(64) NULL,
    cliente_id BIGINT UNSIGNED NULL,
    conta_mesa_id BIGINT UNSIGNED NULL,
    modalidade VARCHAR(15) NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'AGUARDANDO_CONFIRMACAO',
    -- identificação copiada para todas as modalidades (rastreamento por número + CPF)
    nome_responsavel VARCHAR(150) NOT NULL,
    cpf_responsavel CHAR(11) NOT NULL,

    endereco_cep CHAR(8) NULL,
    endereco_logradouro VARCHAR(200) NULL,
    endereco_numero VARCHAR(20) NULL,
    endereco_complemento VARCHAR(100) NULL,
    endereco_bairro VARCHAR(100) NULL,
    endereco_cidade VARCHAR(100) NULL,
    endereco_estado CHAR(2) NULL,

    subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    taxa_entrega DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    observacao TEXT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmado_em DATETIME NULL,
    atualizado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_pedido_numero (numero_pedido),
    UNIQUE KEY uq_pedido_idempotencia (chave_idempotencia),
    KEY idx_pedido_cliente (cliente_id),
    KEY idx_pedido_conta_mesa (conta_mesa_id),
    KEY idx_pedido_estado (estado),
    KEY idx_pedido_cpf (cpf_responsavel),
    KEY idx_pedido_modalidade_criado (modalidade, criado_em),

    CONSTRAINT fk_pedido_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id),
    CONSTRAINT fk_pedido_conta_mesa FOREIGN KEY (conta_mesa_id) REFERENCES conta_mesa(id),

    CONSTRAINT chk_pedido_modalidade CHECK (modalidade IN ('ENTREGA', 'RETIRADA', 'MESA')),
    CONSTRAINT chk_pedido_estado CHECK (estado IN (
        'AGUARDANDO_CONFIRMACAO', 'CONFIRMADO', 'EM_PREPARACAO', 'A_CAMINHO',
        'PRONTO_PARA_RETIRADA', 'SERVIDO', 'CONCLUIDO', 'CANCELADO')),
    CONSTRAINT chk_pedido_valores CHECK (subtotal >= 0 AND taxa_entrega >= 0 AND total >= 0),
    CONSTRAINT chk_pedido_total CHECK (total = subtotal + taxa_entrega),
    CONSTRAINT chk_pedido_mesa_relacao CHECK (
        (modalidade = 'MESA' AND conta_mesa_id IS NOT NULL)
        OR (modalidade <> 'MESA' AND conta_mesa_id IS NULL)),
    CONSTRAINT chk_pedido_entrega_dados CHECK (
        modalidade <> 'ENTREGA'
        OR (cliente_id IS NOT NULL
            AND endereco_cep IS NOT NULL AND endereco_logradouro IS NOT NULL
            AND endereco_numero IS NOT NULL AND endereco_bairro IS NOT NULL
            AND endereco_cidade IS NOT NULL AND endereco_estado IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE item_pedido (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT UNSIGNED NOT NULL,
    produto_id BIGINT UNSIGNED NULL,
    nome_produto_historico VARCHAR(150) NOT NULL,
    tipo_produto_historico VARCHAR(15) NOT NULL,
    quantidade INT UNSIGNED NOT NULL,
    preco_unitario DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    preco_base_historico DECIMAL(10,2) NULL,
    estrategia_historica VARCHAR(15) NULL,
    participa_preco_base_historico BOOLEAN NULL,
    PRIMARY KEY (id),
    KEY idx_item_pedido_pedido (pedido_id),
    KEY idx_item_pedido_produto (produto_id),
    CONSTRAINT fk_item_pedido_pedido FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    CONSTRAINT fk_item_pedido_produto FOREIGN KEY (produto_id) REFERENCES produto(id) ON DELETE SET NULL,
    CONSTRAINT chk_item_tipo CHECK (tipo_produto_historico IN ('SIMPLES', 'COMPOSTO')),
    CONSTRAINT chk_item_quantidade CHECK (quantidade > 0),
    CONSTRAINT chk_item_precos CHECK (preco_unitario >= 0 AND subtotal >= 0)
) ENGINE=InnoDB;

CREATE TABLE escolha_item_pedido (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_pedido_id BIGINT UNSIGNED NOT NULL,
    grupo_id BIGINT UNSIGNED NULL,
    opcao_id BIGINT UNSIGNED NULL,
    nome_grupo_historico VARCHAR(100) NOT NULL,
    nome_opcao_historico VARCHAR(150) NOT NULL,
    modalidade_cobranca_historica VARCHAR(20) NOT NULL,
    quantidade INT UNSIGNED NOT NULL,
    preco_unitario_aplicado DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_escolha_item (item_pedido_id),
    CONSTRAINT fk_escolha_item FOREIGN KEY (item_pedido_id) REFERENCES item_pedido(id),
    CONSTRAINT fk_escolha_grupo FOREIGN KEY (grupo_id) REFERENCES grupo_opcao(id) ON DELETE SET NULL,
    CONSTRAINT fk_escolha_opcao FOREIGN KEY (opcao_id) REFERENCES opcao(id) ON DELETE SET NULL,
    CONSTRAINT chk_escolha_cobranca CHECK (modalidade_cobranca_historica IN ('PRINCIPAL', 'SEPARADA', 'GRATUITA')),
    CONSTRAINT chk_escolha_quantidade CHECK (quantidade > 0),
    CONSTRAINT chk_escolha_precos CHECK (preco_unitario_aplicado >= 0 AND subtotal >= 0)
) ENGINE=InnoDB;

CREATE TABLE adicional_item_pedido (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_pedido_id BIGINT UNSIGNED NOT NULL,
    adicional_id BIGINT UNSIGNED NULL,
    nome_historico VARCHAR(150) NOT NULL,
    quantidade INT UNSIGNED NOT NULL,
    preco_unitario_aplicado DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_adicional_item_pedido (item_pedido_id),
    CONSTRAINT fk_adicional_item FOREIGN KEY (item_pedido_id) REFERENCES item_pedido(id),
    CONSTRAINT fk_adicional_referencia FOREIGN KEY (adicional_id) REFERENCES adicional_simples(id) ON DELETE SET NULL,
    CONSTRAINT chk_adicional_item_quantidade CHECK (quantidade > 0),
    CONSTRAINT chk_adicional_item_precos CHECK (preco_unitario_aplicado >= 0 AND subtotal >= 0)
) ENGINE=InnoDB;

-- =========================================================
-- 6. FECHAMENTO DE MESA E PAGAMENTOS
-- =========================================================

CREATE TABLE fechamento_mesa (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conta_mesa_id BIGINT UNSIGNED NOT NULL,
    total_fechamento DECIMAL(10,2) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    concluido_em DATETIME NULL,
    -- no máximo um fechamento pendente por conta
    pendente_chave BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN estado = 'PENDENTE' THEN conta_mesa_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    KEY idx_fechamento_conta (conta_mesa_id),
    UNIQUE KEY uq_fechamento_pendente (pendente_chave),
    CONSTRAINT fk_fechamento_conta FOREIGN KEY (conta_mesa_id) REFERENCES conta_mesa(id),
    CONSTRAINT chk_fechamento_total CHECK (total_fechamento >= 0),
    CONSTRAINT chk_fechamento_estado CHECK (estado IN ('PENDENTE', 'PAGO', 'CANCELADO'))
) ENGINE=InnoDB;

CREATE TABLE pagamento (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT UNSIGNED NULL,
    fechamento_mesa_id BIGINT UNSIGNED NULL,
    metodo VARCHAR(15) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    valor DECIMAL(10,2) NOT NULL,
    valor_recebido DECIMAL(10,2) NULL,
    confirmado_por BIGINT UNSIGNED NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmado_em DATETIME NULL,
    -- no máximo um pagamento CONFIRMADO por destino (sem pagamentos parciais na V1)
    pedido_confirmado_chave BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN estado = 'CONFIRMADO' THEN pedido_id ELSE NULL END) STORED,
    fechamento_confirmado_chave BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN estado = 'CONFIRMADO' THEN fechamento_mesa_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    KEY idx_pagamento_pedido (pedido_id),
    KEY idx_pagamento_fechamento (fechamento_mesa_id),
    KEY idx_pagamento_estado (estado),
    UNIQUE KEY uq_pagamento_pedido_confirmado (pedido_confirmado_chave),
    UNIQUE KEY uq_pagamento_fechamento_confirmado (fechamento_confirmado_chave),
    CONSTRAINT fk_pagamento_pedido FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    CONSTRAINT fk_pagamento_fechamento FOREIGN KEY (fechamento_mesa_id) REFERENCES fechamento_mesa(id),
    CONSTRAINT fk_pagamento_funcionario FOREIGN KEY (confirmado_por) REFERENCES funcionario(id),
    CONSTRAINT chk_pagamento_destino CHECK (
        (pedido_id IS NOT NULL AND fechamento_mesa_id IS NULL)
        OR (pedido_id IS NULL AND fechamento_mesa_id IS NOT NULL)),
    CONSTRAINT chk_pagamento_metodo CHECK (metodo IN ('DINHEIRO', 'CARTAO', 'PIX')),
    CONSTRAINT chk_pagamento_estado CHECK (estado IN ('PENDENTE', 'CONFIRMADO', 'CANCELADO')),
    CONSTRAINT chk_pagamento_valor CHECK (valor >= 0),
    CONSTRAINT chk_pagamento_confirmacao CHECK (
        estado <> 'CONFIRMADO' OR (confirmado_por IS NOT NULL AND confirmado_em IS NOT NULL))
) ENGINE=InnoDB;

-- =========================================================
-- 7. HISTÓRICO, CANCELAMENTOS E CÓDIGOS
-- =========================================================

CREATE TABLE historico_estado_pedido (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT UNSIGNED NOT NULL,
    estado_anterior VARCHAR(30) NULL,
    estado_novo VARCHAR(30) NOT NULL,
    funcionario_id BIGINT UNSIGNED NULL,
    conclusao_manual BOOLEAN NOT NULL DEFAULT FALSE,
    justificativa TEXT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_historico_pedido_data (pedido_id, criado_em),
    CONSTRAINT fk_historico_pedido FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    CONSTRAINT fk_historico_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario(id),
    CONSTRAINT chk_historico_manual CHECK (conclusao_manual = FALSE OR (funcionario_id IS NOT NULL AND justificativa IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE historico_conta_mesa (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conta_mesa_id BIGINT UNSIGNED NOT NULL,
    estado_anterior VARCHAR(20) NULL,
    estado_novo VARCHAR(20) NOT NULL,
    funcionario_id BIGINT UNSIGNED NULL,
    justificativa TEXT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_historico_mesa_data (conta_mesa_id, criado_em),
    CONSTRAINT fk_historico_mesa_conta FOREIGN KEY (conta_mesa_id) REFERENCES conta_mesa(id),
    CONSTRAINT fk_historico_mesa_funcionario FOREIGN KEY (funcionario_id) REFERENCES funcionario(id)
) ENGINE=InnoDB;

CREATE TABLE solicitacao_cancelamento (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT UNSIGNED NOT NULL,
    solicitado_por_tipo VARCHAR(20) NOT NULL,
    cliente_id BIGINT UNSIGNED NULL,
    solicitado_por_funcionario_id BIGINT UNSIGNED NULL,
    solicitado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    decidido_por BIGINT UNSIGNED NULL,
    decidido_em DATETIME NULL,
    justificativa TEXT NULL,
    -- no máximo uma solicitação pendente por pedido
    pendente_chave BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN estado = 'PENDENTE' THEN pedido_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    KEY idx_cancelamento_pedido (pedido_id),
    KEY idx_cancelamento_estado (estado),
    UNIQUE KEY uq_cancelamento_pendente (pendente_chave),
    CONSTRAINT fk_cancelamento_pedido FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    CONSTRAINT fk_cancelamento_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id),
    CONSTRAINT fk_cancelamento_solicitante_func FOREIGN KEY (solicitado_por_funcionario_id) REFERENCES funcionario(id),
    CONSTRAINT fk_cancelamento_decisor FOREIGN KEY (decidido_por) REFERENCES funcionario(id),
    CONSTRAINT chk_cancelamento_solicitante CHECK (solicitado_por_tipo IN ('CLIENTE', 'VISITANTE', 'FUNCIONARIO')),
    CONSTRAINT chk_cancelamento_estado CHECK (estado IN ('PENDENTE', 'APROVADO', 'REJEITADO'))
) ENGINE=InnoDB;

-- codigo_hash = HMAC-SHA256 (hex) do código com segredo do servidor: determinístico (permite busca)
-- e sem expor o código em claro.
CREATE TABLE codigo_acompanhamento (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT UNSIGNED NOT NULL,
    codigo_hash CHAR(64) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    expira_em DATETIME NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_acompanhamento_hash (codigo_hash),
    KEY idx_acompanhamento_pedido (pedido_id),
    CONSTRAINT fk_acompanhamento_pedido FOREIGN KEY (pedido_id) REFERENCES pedido(id)
) ENGINE=InnoDB;

-- Código curto (6 dígitos): sem unicidade global; sempre validado dentro do pedido.
CREATE TABLE codigo_confirmacao (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pedido_id BIGINT UNSIGNED NOT NULL,
    codigo_hash CHAR(64) NOT NULL,
    tipo VARCHAR(15) NOT NULL,
    tentativas INT UNSIGNED NOT NULL DEFAULT 0,
    expira_em DATETIME NOT NULL,
    utilizado_em DATETIME NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ativo_chave BIGINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN ativo = 1 THEN pedido_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    KEY idx_confirmacao_pedido (pedido_id),
    UNIQUE KEY uq_confirmacao_ativo (ativo_chave),
    CONSTRAINT fk_confirmacao_pedido FOREIGN KEY (pedido_id) REFERENCES pedido(id),
    CONSTRAINT chk_confirmacao_tipo CHECK (tipo IN ('ENTREGA', 'RETIRADA'))
) ENGINE=InnoDB;
