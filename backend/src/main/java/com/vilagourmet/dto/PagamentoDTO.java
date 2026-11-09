package com.vilagourmet.dto;

import com.vilagourmet.model.enums.EstadoPagamento;
import com.vilagourmet.model.enums.MetodoPagamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PagamentoDTO {

    private PagamentoDTO() {
    }

    /** Confirmação manual do recebimento. Em dinheiro, {@code valorRecebido} permite calcular o troco. */
    public record ConfirmarPagamentoRequest(@NotNull MetodoPagamento metodo, @DecimalMin("0.00") BigDecimal valorRecebido) {
    }

    public record PagamentoResponse(Long id, MetodoPagamento metodo, EstadoPagamento estado, BigDecimal valor,
                                    BigDecimal valorRecebido, BigDecimal troco, LocalDateTime confirmadoEm) {
    }
}
