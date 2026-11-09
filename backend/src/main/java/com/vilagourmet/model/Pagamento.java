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
@Table(name = "pagamento")
@Getter
@Setter
public class Pagamento extends EntidadeBase {

    private Long pedidoId;

    private Long fechamentoMesaId;

    @Enumerated(EnumType.STRING)
    private MetodoPagamento metodo;

    @Enumerated(EnumType.STRING)
    private EstadoPagamento estado = EstadoPagamento.PENDENTE;

    private BigDecimal valor;

    private BigDecimal valorRecebido;

    private Long confirmadoPor;

    private LocalDateTime criadoEm = LocalDateTime.now();

    private LocalDateTime confirmadoEm;
}
