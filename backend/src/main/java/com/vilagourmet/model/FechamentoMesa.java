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
@Table(name = "fechamento_mesa")
@Getter
@Setter
public class FechamentoMesa extends EntidadeBase {

    private Long contaMesaId;

    private BigDecimal totalFechamento;

    @Enumerated(EnumType.STRING)
    private EstadoFechamento estado = EstadoFechamento.PENDENTE;

    private LocalDateTime criadoEm = LocalDateTime.now();

    private LocalDateTime concluidoEm;
}
