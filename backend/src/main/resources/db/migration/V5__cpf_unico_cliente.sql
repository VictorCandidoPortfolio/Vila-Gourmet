-- O CPF passa a ser único por conta de cliente (antes era apenas indexado).
-- Se já existirem clientes com o mesmo CPF, esta migração falha: veja-os com
--   SELECT cpf, COUNT(*) FROM cliente GROUP BY cpf HAVING COUNT(*) > 1;
-- e deixe um único cadastro por CPF antes de subir a aplicação de novo.
ALTER TABLE cliente
    DROP INDEX idx_cliente_cpf,
    ADD UNIQUE KEY uq_cliente_cpf (cpf);
