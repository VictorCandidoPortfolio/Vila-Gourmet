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
@Table(name = "opcao")
@Getter
@Setter
public class Opcao extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_opcao_id")
    private GrupoOpcao grupo;

    private Long insumoId;

    private String nome;

    private BigDecimal preco = BigDecimal.ZERO;

    private int minimo;

    private int maximo = 1;

    private boolean disponivel = true;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
