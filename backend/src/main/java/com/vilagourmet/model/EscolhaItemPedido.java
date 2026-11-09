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
@Table(name = "escolha_item_pedido")
@Getter
@Setter
public class EscolhaItemPedido extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_pedido_id")
    private ItemPedido item;

    private Long grupoId;

    private Long opcaoId;

    private String nomeGrupoHistorico;

    private String nomeOpcaoHistorico;

    private String modalidadeCobrancaHistorica;

    private int quantidade;

    private BigDecimal precoUnitarioAplicado;

    private BigDecimal subtotal;
}
