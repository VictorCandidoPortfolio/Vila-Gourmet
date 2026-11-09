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
@Table(name = "historico_conta_mesa")
@Getter
@Setter
public class HistoricoContaMesa extends EntidadeBase {

    private Long contaMesaId;

    private String estadoAnterior;

    private String estadoNovo;

    private Long funcionarioId;

    private String justificativa;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
