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
@Table(name = "item_pedido")
@Getter
@Setter
public class ItemPedido extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    private Long produtoId;

    private String nomeProdutoHistorico;

    private String tipoProdutoHistorico;

    private int quantidade;

    private BigDecimal precoUnitario;

    private BigDecimal subtotal;

    private BigDecimal precoBaseHistorico;

    private String estrategiaHistorica;

    private Boolean participaPrecoBaseHistorico;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EscolhaItemPedido> escolhas = new ArrayList<>();

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AdicionalItemPedido> adicionais = new ArrayList<>();
}
