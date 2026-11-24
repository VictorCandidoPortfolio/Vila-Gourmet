package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vilagourmet.dto.MesaDTO.AbrirMesaRequest;
import com.vilagourmet.dto.MesaDTO.MesaPedidoRequest;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.ContaMesa;
import com.vilagourmet.model.Cliente;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.enums.EstadoContaMesa;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.repository.ContaMesaRepository;
import com.vilagourmet.repository.ClienteRepository;
import com.vilagourmet.repository.FechamentoMesaRepository;
import com.vilagourmet.repository.HistoricoContaMesaRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MesaServiceTest {

    private final ContaMesaRepository contas = mock(ContaMesaRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final RateLimiterService limiter = mock(RateLimiterService.class);
    private final AberturaLojaService abertura = mock(AberturaLojaService.class);
    private MesaService mesas;

    @BeforeEach
    void preparar() {
        mesas = new MesaService(contas, mock(HistoricoContaMesaRepository.class),
                mock(FechamentoMesaRepository.class), mock(PagamentoRepository.class), pedidos,
                mock(PedidoService.class), mock(PedidoMapper.class), limiter, abertura, clientes);
    }

    @Test
    void naoAbreDuasContasAtivasParaOMesmoCpf() {
        when(contas.existsByCpfResponsavelAndEstadoIn(eq("52998224725"), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> mesas.abrir(new AbrirMesaRequest("Maria", "529.982.247-25")))
                .isInstanceOfSatisfying(NegocioException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getMessage()).contains("Já existe uma conta de mesa ativa");
                });
        verify(contas, never()).saveAndFlush(any());
    }

    @Test
    void restricaoDoBancoTambemBarraAberturasSimultaneas() {
        when(contas.existsByCodigoConta(any())).thenReturn(false);
        when(contas.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_conta_mesa_cpf_ativa"));

        assertThatThrownBy(() -> mesas.abrir(new AbrirMesaRequest("Maria", "52998224725")))
                .isInstanceOfSatisfying(NegocioException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(409));
    }

    @Test
    void acessaContaAtivaPeloCpfSemExporOsPedidos() {
        ContaMesa conta = new ContaMesa();
        conta.setCodigoConta("MABC123");
        conta.setNomeResponsavel("Maria");
        conta.setCpfResponsavel("52998224725");
        conta.setEstado(EstadoContaMesa.ABERTA);
        when(contas.findFirstByCpfResponsavelAndEstadoInOrderByAbertaEmDesc(
                eq("52998224725"), anyCollection())).thenReturn(Optional.of(conta));

        var resposta = mesas.acessarPorCpf("529.982.247-25", "127.0.0.1");

        assertThat(resposta.codigoConta()).isEqualTo("MABC123");
        assertThat(resposta.nomeResponsavel()).isEqualTo("Maria");
        assertThat(resposta.estado()).isEqualTo(EstadoContaMesa.ABERTA);
        verify(limiter).limpar("mesa-acesso:127.0.0.1");
    }

    @Test
    void cpfSemContaAtivaRegistraFalhaSemRevelarOutrosDados() {
        assertThatThrownBy(() -> mesas.acessarPorCpf("52998224725", "127.0.0.1"))
                .hasMessage("Não há conta de mesa ativa para este CPF");
        verify(limiter).registrarFalha("mesa-acesso:127.0.0.1");
    }

    @Test
    void consultaMesaAtivaPeloCpfDoPerfil() {
        cadastrarPerfil();
        assertThat(mesas.mesaDoCliente(17L)).isEmpty();

        ContaMesa conta = contaAberta();
        conta.setEstado(EstadoContaMesa.EM_FECHAMENTO);
        when(contas.findFirstByCpfResponsavelAndEstadoInOrderByAbertaEmDesc(
                "52998224725", List.of(EstadoContaMesa.ABERTA, EstadoContaMesa.EM_FECHAMENTO))).thenReturn(Optional.of(conta));

        assertThat(mesas.mesaDoCliente(17L)).hasValueSatisfying(mesa -> {
            assertThat(mesa.codigoConta()).isEqualTo(conta.getCodigoConta());
            assertThat(mesa.estado()).isEqualTo(EstadoContaMesa.EM_FECHAMENTO);
        });
    }

    @Test
    void abreMesaComNomeECpfDoPerfil() {
        cadastrarPerfil();

        var resposta = mesas.abrirParaCliente(17L);

        assertThat(resposta.nomeResponsavel()).isEqualTo("Maria");
        assertThat(resposta.estado()).isEqualTo(EstadoContaMesa.ABERTA);
        var conta = org.mockito.ArgumentCaptor.forClass(ContaMesa.class);
        verify(contas).saveAndFlush(conta.capture());
        assertThat(conta.getValue().getCpfResponsavel()).isEqualTo("52998224725");
        verify(abertura).exigirVendasOnline();
    }

    @Test
    void codigoDeOutraMesaNaoAutorizaPedidoMesmoComCpfNoCorpo() {
        cadastrarPerfil();
        when(contas.findFirstByCpfResponsavelAndEstadoInOrderByAbertaEmDesc(eq("52998224725"), anyCollection()))
                .thenReturn(Optional.of(contaAberta()));

        assertThatThrownBy(() -> mesas.pedidoDoCliente(17L, "MOUTRA", new MesaPedidoRequest("11144477735", List.of(), null), null))
                .isInstanceOf(NaoEncontradoException.class);
    }

    private void cadastrarPerfil() {
        Cliente cliente = new Cliente();
        cliente.setNome("Maria");
        cliente.setCpf("52998224725");
        when(clientes.findById(17L)).thenReturn(Optional.of(cliente));
    }

    @Test
    void encerraSemFechamentoUmaMesaSemConsumo() {
        ContaMesa conta = contaAberta();
        when(contas.findByCodigoConta("MABC123")).thenReturn(Optional.of(conta));
        when(pedidos.findByContaMesaIdOrderByIdAsc(10L)).thenReturn(List.of());

        var resposta = mesas.encerrarSemFechamento("MABC123", 7L, "Aberta por engano");

        assertThat(resposta.estado()).isEqualTo(EstadoContaMesa.ENCERRADA);
        assertThat(conta.getEncerradaEm()).isNotNull();
    }

    @Test
    void naoEncerraSemFechamentoQuandoHaConsumo() {
        ContaMesa conta = contaAberta();
        Pedido pedido = new Pedido();
        pedido.setEstado(EstadoPedido.CONFIRMADO);
        when(contas.findByCodigoConta("MABC123")).thenReturn(Optional.of(conta));
        when(pedidos.findByContaMesaIdOrderByIdAsc(10L)).thenReturn(List.of(pedido));

        assertThatThrownBy(() -> mesas.encerrarSemFechamento("MABC123", 7L, "Aberta por engano"))
                .isInstanceOfSatisfying(NegocioException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getMessage()).contains("possui pedidos");
                });
        assertThat(conta.getEstado()).isEqualTo(EstadoContaMesa.ABERTA);
    }

    private static ContaMesa contaAberta() {
        ContaMesa conta = new ContaMesa();
        conta.setId(10L);
        conta.setCodigoConta("MABC123");
        conta.setNomeResponsavel("Maria");
        conta.setEstado(EstadoContaMesa.ABERTA);
        return conta;
    }
}
