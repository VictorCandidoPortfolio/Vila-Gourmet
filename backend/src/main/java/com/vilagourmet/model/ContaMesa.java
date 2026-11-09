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
@Table(name = "conta_mesa")
@Getter
@Setter
public class ContaMesa extends EntidadeBase {

    private String codigoConta;

    private String nomeResponsavel;

    private String cpfResponsavel;

    @Enumerated(EnumType.STRING)
    private EstadoContaMesa estado = EstadoContaMesa.ABERTA;

    private LocalDateTime abertaEm = LocalDateTime.now();

    private LocalDateTime encerradaEm;
}
