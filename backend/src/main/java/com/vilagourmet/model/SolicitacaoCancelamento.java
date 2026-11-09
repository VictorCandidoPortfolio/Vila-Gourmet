package com.vilagourmet.model;

import com.vilagourmet.model.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "solicitacao_cancelamento")
@Getter
@Setter
public class SolicitacaoCancelamento extends EntidadeBase {

    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    private TipoSolicitante solicitadoPorTipo;

    private Long clienteId;

    private Long solicitadoPorFuncionarioId;

    private LocalDateTime solicitadoEm = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private EstadoCancelamento estado = EstadoCancelamento.PENDENTE;

    private Long decididoPor;

    private LocalDateTime decididoEm;

    private String justificativa;
}
