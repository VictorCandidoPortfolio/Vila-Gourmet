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
@Table(name = "pedido")
@Getter
@Setter
public class Pedido extends EntidadeBase {

    private String numeroPedido;

    private String chaveIdempotencia;

    private Long clienteId;

    private Long contaMesaId;

    /** Sessão de caixa ativa quando a venda foi criada, inclusive para pedidos da loja. */
    private Long caixaId;

    @Enumerated(EnumType.STRING)
    private Modalidade modalidade;

    @Enumerated(EnumType.STRING)
    private EstadoPedido estado = EstadoPedido.AGUARDANDO_CONFIRMACAO;

    private String nomeResponsavel;

    private String cpfResponsavel;

    private String enderecoCep;

    private String enderecoLogradouro;

    private String enderecoNumero;

    private String enderecoComplemento;

    private String enderecoBairro;

    private String enderecoCidade;

    private String enderecoEstado;

    private BigDecimal subtotal = BigDecimal.ZERO;

    private BigDecimal taxaEntrega = BigDecimal.ZERO;

    private BigDecimal total = BigDecimal.ZERO;

    private String observacao;

    private LocalDateTime criadoEm = LocalDateTime.now();

    private LocalDateTime confirmadoEm;

    private LocalDateTime atualizadoEm = LocalDateTime.now();

    private LocalDateTime ultimaEdicaoCaixaEm;

    private Long ultimaEdicaoCaixaPor;

    private String justificativaUltimaEdicao;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> itens = new ArrayList<>();
}
