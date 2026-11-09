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
@Table(name = "historico_estado_pedido")
@Getter
@Setter
public class HistoricoEstadoPedido extends EntidadeBase {

    private Long pedidoId;

    private String estadoAnterior;

    private String estadoNovo;

    private Long funcionarioId;

    private boolean conclusaoManual;

    private String justificativa;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
