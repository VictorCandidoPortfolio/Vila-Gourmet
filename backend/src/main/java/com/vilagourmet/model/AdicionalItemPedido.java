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
@Table(name = "adicional_item_pedido")
@Getter
@Setter
public class AdicionalItemPedido extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_pedido_id")
    private ItemPedido item;

    private Long adicionalId;

    private String nomeHistorico;

    private int quantidade;

    private BigDecimal precoUnitarioAplicado;

    private BigDecimal subtotal;
}
