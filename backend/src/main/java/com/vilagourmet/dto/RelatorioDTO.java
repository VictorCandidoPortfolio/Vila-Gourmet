package com.vilagourmet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public final class RelatorioDTO {

    private RelatorioDTO() {
    }

    /**
     * Pedidos e distribuições consideram todos os pedidos criados no período (inclusive cancelados,
     * visíveis na distribuição por estado). {@code totalVendido} exclui CANCELADO e
     * AGUARDANDO_CONFIRMACAO e é somado por pedido (mesa nunca é contada duas vezes);
     * {@code totalRecebido} soma pagamentos confirmados no período, independentemente do total vendido.
     */
    public record RelatorioOperacionalResponse(LocalDate inicio, LocalDate fim, long quantidadePedidos,
                                               Map<String, Long> porEstado, Map<String, Long> porModalidade,
                                               BigDecimal totalVendido, BigDecimal totalRecebido) {
    }
}
