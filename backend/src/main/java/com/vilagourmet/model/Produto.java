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
@Table(name = "produto")
@Getter
@Setter
public class Produto extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    private TipoProduto tipo;

    private String nome;

    private String descricao;

    private String imagemUrl;

    private BigDecimal precoBase;

    private boolean disponivel = true;

    @Enumerated(EnumType.STRING)
    private StatusProduto status = StatusProduto.RASCUNHO;

    private LocalDateTime criadoEm = LocalDateTime.now();

    private LocalDateTime atualizadoEm = LocalDateTime.now();
}
