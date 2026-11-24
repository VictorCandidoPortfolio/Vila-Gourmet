package com.vilagourmet.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Auditoria imutável das correções feitas em vendas pelo histórico do caixa. */
@Entity
@Table(name = "historico_edicao_venda")
@Getter
@Setter
public class HistoricoEdicaoVenda extends EntidadeBase {

    private Long pedidoId;
    private Long funcionarioId;
    private String nomeAnterior;
    private String nomeNovo;
    private boolean cpfAlterado;
    private String observacaoAnterior;
    private String observacaoNova;
    private String metodoAnterior;
    private String metodoNovo;
    private String justificativa;
    private LocalDateTime criadoEm = LocalDateTime.now();
}
