package com.vilagourmet.dto;

import com.vilagourmet.dto.PedidoDTO.ItemRequest;
import com.vilagourmet.dto.PedidoDTO.PedidoResponse;
import com.vilagourmet.model.enums.EstadoContaMesa;
import com.vilagourmet.model.enums.EstadoFechamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MesaDTO {

    private MesaDTO() {
    }

    public record AbrirMesaRequest(@NotBlank @Size(max = 150) String nome, @NotBlank String cpf) {
    }

    /**
     * Pedido em uma conta de mesa. Visitante informa o CPF do responsável; funcionário
     * (endpoint de gestão) localiza a conta apenas pelo código.
     */
    public record MesaPedidoRequest(String cpfResponsavel, @NotEmpty @Valid List<ItemRequest> itens, String observacao) {
    }

    public record FechamentoResponse(Long id, BigDecimal total, EstadoFechamento estado, LocalDateTime criadoEm,
                                     LocalDateTime concluidoEm, PagamentoDTO.PagamentoResponse pagamento) {
    }

    public record HistoricoMesaResponse(String estadoAnterior, String estadoNovo, Long funcionarioId,
                                        String justificativa, LocalDateTime em) {
    }

    public record ContaMesaResponse(Long id, String codigoConta, String nomeResponsavel, String cpfResponsavel,
                                    EstadoContaMesa estado, LocalDateTime abertaEm, LocalDateTime encerradaEm,
                                    BigDecimal total, List<PedidoResponse> pedidos, FechamentoResponse fechamento,
                                    List<HistoricoMesaResponse> historico) {
    }

    public record MesaAbertaResponse(ContaMesaResponse conta) {
    }
}
