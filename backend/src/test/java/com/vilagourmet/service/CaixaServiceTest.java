package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vilagourmet.dto.CaixaDTO.CaixaStatusResponse;
import com.vilagourmet.dto.CaixaDTO.FechamentoCaixaResponse;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Caixa;
import com.vilagourmet.model.ContaMesa;
import com.vilagourmet.model.Pagamento;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.SolicitacaoCancelamento;
import com.vilagourmet.model.enums.EstadoCancelamento;
import com.vilagourmet.model.enums.EstadoContaMesa;
import com.vilagourmet.model.enums.EstadoPagamento;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.repository.CaixaRepository;
import com.vilagourmet.repository.ContaMesaRepository;
import com.vilagourmet.repository.FuncionarioRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import com.vilagourmet.repository.SolicitacaoCancelamentoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Abertura e fechamento do caixa e filtros de situação dos pedidos. */
class CaixaServiceTest {

    private final AberturaLojaService abertura = mock(AberturaLojaService.class);
    private final CaixaRepository caixas = mock(CaixaRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final PagamentoRepository pagamentos = mock(PagamentoRepository.class);
    private final ContaMesaRepository contas = mock(ContaMesaRepository.class);
    private final SolicitacaoCancelamentoRepository solicitacoes = mock(SolicitacaoCancelamentoRepository.class);
    private final PedidoEstadoService estados = mock(PedidoEstadoService.class);
    private final CancelamentoService cancelamento = mock(CancelamentoService.class);
    private final MesaService mesas = mock(MesaService.class);
    private CaixaService caixa;
    private final Caixa aberto = new Caixa();

    @BeforeEach
    void preparar() {
        aberto.setId(1L);
        aberto.setAbertoPor(7L);
        caixa = new CaixaService(abertura, caixas, pedidos, pagamentos, contas, solicitacoes, estados, cancelamento, mesas,
                mock(PedidoService.class), mock(FuncionarioRepository.class));
        when(pagamentos.findByPedidoIdAndEstado(anyLong(), any())).thenReturn(Optional.empty());
        when(solicitacoes.findByPedidoIdAndEstado(anyLong(), any())).thenReturn(Optional.empty());
        when(contas.findByEstadoOrderByAbertaEmDesc(EstadoContaMesa.ABERTA)).thenReturn(List.of());
    }

    private static Pedido pedido(long id, String numero, EstadoPedido estado) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setNumeroPedido(numero);
        p.setEstado(estado);
        return p;
    }

    private void caixaAberto(Pedido... emAberto) {
        when(abertura.caixaAberto()).thenReturn(Optional.of(aberto));
        when(abertura.aberta()).thenReturn(true);
        when(pedidos.findByEstadoNotIn(PedidoService.FINAIS)).thenReturn(List.of(emAberto));
    }

    @Test
    void statusFechadoNaoTemPedidosNemBloqueios() {
        when(abertura.caixaAberto()).thenReturn(Optional.empty());
        CaixaStatusResponse s = caixa.status();
        assertThat(s.aberto()).isFalse();
        assertThat(s.pedidosEmAberto()).isZero();
    }

    @Test
    void naoAbreOCaixaDuasVezes() {
        when(abertura.aberta()).thenReturn(true);
        assertThatThrownBy(() -> caixa.abrir(7L)).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(409));
        verify(caixas, never()).saveAndFlush(any());
    }

    @Test
    void aberturaSimultaneaEBarradaPelaChaveUnicaDoBanco() {
        when(abertura.aberta()).thenReturn(false);
        when(caixas.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_caixa_um_aberto"));
        assertThatThrownBy(() -> caixa.abrir(7L)).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void fechaSemPedidosEmAbertoSemPedirConfirmacao() {
        caixaAberto();
        FechamentoCaixaResponse r = caixa.fechar(7L, false);
        assertThat(r.pedidosCancelados()).isZero();
        assertThat(aberto.getFechadoEm()).isNotNull();
        assertThat(aberto.getFechadoPor()).isEqualTo(7L);
    }

    @Test
    void comPedidosEmAbertoExigeConfirmacaoENaoCancelaNada() {
        caixaAberto(pedido(1, "P1", EstadoPedido.CONFIRMADO), pedido(2, "P2", EstadoPedido.EM_PREPARACAO));
        assertThatThrownBy(() -> caixa.fechar(7L, false)).isInstanceOfSatisfying(NegocioException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(409);
            assertThat(e.getMessage()).contains("2 pedidos não concluídos").contains("serão cancelados");
            assertThat(e.getDetalhes()).containsExactly("P1", "P2");
        });
        verify(estados, never()).cancelar(any(), any(), anyString());
        assertThat(aberto.getFechadoEm()).isNull();
    }

    @Test
    void confirmadoCancelaOsPedidosEEncerraAsContasDeMesaAbertas() {
        Pedido a = pedido(1, "P1", EstadoPedido.CONFIRMADO);
        Pedido b = pedido(2, "P2", EstadoPedido.A_CAMINHO);
        caixaAberto(a, b);
        ContaMesa conta = new ContaMesa();
        conta.setId(9L);
        when(contas.findByEstadoOrderByAbertaEmDesc(EstadoContaMesa.ABERTA)).thenReturn(List.of(conta));

        FechamentoCaixaResponse r = caixa.fechar(7L, true);

        verify(estados).cancelar(a, 7L, CaixaService.MOTIVO_FECHAMENTO);
        verify(estados).cancelar(b, 7L, CaixaService.MOTIVO_FECHAMENTO);
        verify(mesas).encerrarSemCobranca(9L, 7L, CaixaService.MOTIVO_FECHAMENTO);
        assertThat(r.pedidosCancelados()).isEqualTo(2);
        assertThat(r.contasEncerradas()).isEqualTo(1);
        assertThat(aberto.getPedidosCancelados()).isEqualTo(2);
        assertThat(aberto.getFechadoEm()).isNotNull();
    }

    @Test
    void solicitacaoDeCancelamentoPendenteEAprovadaEmVezDeCancelarPorFora() {
        Pedido a = pedido(1, "P1", EstadoPedido.CONFIRMADO);
        caixaAberto(a);
        SolicitacaoCancelamento s = new SolicitacaoCancelamento();
        s.setId(33L);
        when(solicitacoes.findByPedidoIdAndEstado(1L, EstadoCancelamento.PENDENTE)).thenReturn(Optional.of(s));
        caixa.fechar(7L, true);
        verify(cancelamento).decidir(33L, true, CaixaService.MOTIVO_FECHAMENTO, 7L);
        verify(estados, never()).cancelar(any(), any(), anyString());
    }

    @Test
    void pedidoComPagamentoConfirmadoImpedeOFechamento() {
        Pedido a = pedido(1, "P1", EstadoPedido.EM_PREPARACAO);
        Pedido b = pedido(2, "P2", EstadoPedido.CONFIRMADO);
        caixaAberto(a, b);
        when(pagamentos.findByPedidoIdAndEstado(1L, EstadoPagamento.CONFIRMADO)).thenReturn(Optional.of(new Pagamento()));
        assertThatThrownBy(() -> caixa.fechar(7L, true)).isInstanceOfSatisfying(NegocioException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(422);
            assertThat(e.getDetalhes()).hasSize(1);
            assertThat(e.getDetalhes().get(0)).contains("P1").contains("pagamento já confirmado");
        });
        verify(estados, never()).cancelar(any(), any(), anyString());
        assertThat(aberto.getFechadoEm()).isNull();
    }

    @Test
    void contaDeMesaEmFechamentoImpedeOFechamento() {
        Pedido a = pedido(1, "P1", EstadoPedido.SERVIDO);
        a.setContaMesaId(9L);
        caixaAberto(a);
        ContaMesa conta = new ContaMesa();
        conta.setId(9L);
        conta.setEstado(EstadoContaMesa.EM_FECHAMENTO);
        when(contas.findById(9L)).thenReturn(Optional.of(conta));
        assertThatThrownBy(() -> caixa.fechar(7L, true)).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.getDetalhes().get(0)).contains("conta de mesa está em fechamento"));
    }

    @Test
    void naoFechaUmCaixaJaFechado() {
        when(abertura.caixaAberto()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> caixa.fechar(7L, true)).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    // ------------------------------------------------------------ filtros de situação dos pedidos

    @Test
    void emAndamentoExcluiConcluidosECancelados() {
        assertThat(PedidoService.estadosDe(null, PedidoService.Situacao.ANDAMENTO))
                .doesNotContain(EstadoPedido.CONCLUIDO, EstadoPedido.CANCELADO)
                .contains(EstadoPedido.AGUARDANDO_CONFIRMACAO, EstadoPedido.A_CAMINHO, EstadoPedido.SERVIDO);
    }

    @Test
    void historicoSoTemConcluidosECancelados() {
        assertThat(PedidoService.estadosDe(null, PedidoService.Situacao.FINALIZADOS))
                .containsExactlyInAnyOrder(EstadoPedido.CONCLUIDO, EstadoPedido.CANCELADO);
    }

    @Test
    void estadoEspecificoRespeitaASituacaoESemFiltroTrazTodos() {
        assertThat(PedidoService.estadosDe(EstadoPedido.CONCLUIDO, PedidoService.Situacao.FINALIZADOS)).containsExactly(EstadoPedido.CONCLUIDO);
        assertThat(PedidoService.estadosDe(EstadoPedido.CONCLUIDO, PedidoService.Situacao.ANDAMENTO)).isEmpty();
        assertThat(PedidoService.estadosDe(null, null)).hasSize(EstadoPedido.values().length);
    }
}
