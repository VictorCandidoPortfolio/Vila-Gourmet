package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.repository.HistoricoEstadoPedidoRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RastreamentoServiceTest {

    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final RastreamentoService rastreamento = new RastreamentoService(pedidos,
            mock(HistoricoEstadoPedidoRepository.class), mock(PagamentoRepository.class),
            mock(CodigoService.class), mock(RateLimiterService.class));

    @Test
    void acompanhaPedidoVinculadoAoClienteDaSessao() {
        Pedido pedido = new Pedido();
        pedido.setId(12L);
        pedido.setNumeroPedido("P20261124-ABCDE");
        when(pedidos.findByIdAndClienteId(12L, 34L)).thenReturn(Optional.of(pedido));

        assertThat(rastreamento.consultarDoCliente(12L, 34L).numeroPedido()).isEqualTo(pedido.getNumeroPedido());
        assertThatThrownBy(() -> rastreamento.consultarDoCliente(12L, 56L))
                .isInstanceOf(NaoEncontradoException.class);
    }
}
