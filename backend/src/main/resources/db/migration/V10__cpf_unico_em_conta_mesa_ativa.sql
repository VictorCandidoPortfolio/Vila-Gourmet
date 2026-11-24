-- Um CPF pode ter várias contas históricas, mas apenas uma ainda ativa.
-- A coluna gerada vira NULL nas contas encerradas; o MySQL permite vários NULLs
-- em um índice único e bloqueia aberturas simultâneas para o mesmo CPF.
ALTER TABLE conta_mesa
    ADD COLUMN cpf_conta_ativa CHAR(11) GENERATED ALWAYS AS (
        CASE WHEN estado IN ('ABERTA', 'EM_FECHAMENTO') THEN cpf_responsavel ELSE NULL END
    ) STORED,
    ADD UNIQUE KEY uq_conta_mesa_cpf_ativa (cpf_conta_ativa);
