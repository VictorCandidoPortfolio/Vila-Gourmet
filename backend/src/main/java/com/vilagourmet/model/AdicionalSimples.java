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
@Table(name = "adicional_simples")
@Getter
@Setter
public class AdicionalSimples extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private String nome;

    private BigDecimal preco = BigDecimal.ZERO;

    private boolean disponivel = true;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
