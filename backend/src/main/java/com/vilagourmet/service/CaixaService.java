package com.vilagourmet.service;

import com.vilagourmet.dto.CaixaDTO.AbrirMesaCaixaRequest;
import com.vilagourmet.dto.CaixaDTO.CaixaStatusResponse;
import com.vilagourmet.dto.CaixaDTO.FechamentoCaixaResponse;
import com.vilagourmet.dto.CaixaDTO.PedidoCaixaRequest;
import com.vilagourmet.dto.MesaDTO.ContaMesaResponse;
import com.vilagourmet.dto.MesaDTO.MesaPedidoRequest;
import com.vilagourmet.dto.PedidoDTO.PedidoCriadoResponse;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Caixa;
import com.vilagourmet.model.ContaMesa;
import com.vilagourmet.model.Funcionario;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.enums.EstadoContaMesa;
import com.vilagourmet.model.enums.EstadoPagamento;
import com.vilagourmet.model.enums.Modalidade;
import com.vilagourmet.repository.CaixaRepository;
import com.vilagourmet.repository.ContaMesaRepository;
import com.vilagourmet.repository.FuncionarioRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import com.vilagourmet.repository.SolicitacaoCancelamentoRepository;
import com.vilagourmet.model.enums.EstadoCancelamento;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Frente de caixa. A pizzaria só vende com o caixa aberto. Ao fechar, os pedidos não concluídos são cancelados
 * (com confirmação explícita); pedidos que não podem ser cancelados pelas regras do sistema impedem o fechamento.
 */
@Service
public class CaixaService {

    static final String MOTIVO_FECHAMENTO = "Caixa fechado: pedido não concluído";

    private final AberturaLojaService abertura;
    private final CaixaRepository caixas;
    private final PedidoRepository pedidos;
    private final PagamentoRepository pagamentos;
    private final ContaMesaRepository contas;
    private final SolicitacaoCancelamentoRepository solicitacoes;
    private final PedidoEstadoService estados;
    private final CancelamentoService cancelamento;
    private final MesaService mesas;
    private final PedidoService pedidoService;
    private final FuncionarioRepository funcionarios;

    public CaixaService(AberturaLojaService abertura, CaixaRepository caixas, PedidoRepository pedidos,
                        PagamentoRepository pagamentos, ContaMesaRepository contas,
                        SolicitacaoCancelamentoRepository solicitacoes, PedidoEstadoService estados,
                        CancelamentoService cancelamento, MesaService mesas, PedidoService pedidoService,
                        FuncionarioRepository funcionarios) {
        this.abertura = abertura;
        this.caixas = caixas;
        this.pedidos = pedidos;
        this.pagamentos = pagamentos;
        this.contas = contas;
        this.solicitacoes = solicitacoes;
        this.estados = estados;
        this.cancelamento = cancelamento;
        this.mesas = mesas;
        this.pedidoService = pedidoService;
        this.funcionarios = funcionarios;
    }

    @Transactional(readOnly = true)
    public CaixaStatusResponse status() {
        Caixa c = abertura.caixaAberto().orElse(null);
        if (c == null) {
            return new CaixaStatusResponse(false, null, null, 0, List.of());
        }
        List<Pedido> abertos = pedidos.findByEstadoNotIn(PedidoService.FINAIS);
        String quem = funcionarios.findById(c.getAbertoPor()).map(Funcionario::getNome).orElse(null);
        return new CaixaStatusResponse(true, c.getAbertoEm(), quem, abertos.size(), bloqueios(abertos));
    }

    @Transactional
    public CaixaStatusResponse abrir(Long funcionarioId) {
        if (abertura.aberta()) {
            throw new NegocioException(HttpStatus.CONFLICT, "O caixa já está aberto");
        }
        Caixa c = new Caixa();
        c.setAbertoPor(funcionarioId);
        try {
            caixas.saveAndFlush(c); // a chave única do banco impede dois caixas abertos ao mesmo tempo
        } catch (DataIntegrityViolationException e) {
            throw new NegocioException(HttpStatus.CONFLICT, "O caixa já está aberto");
        }
        return status();
    }

    /**
     * Fecha o caixa. Com pedidos não concluídos, só fecha se {@code confirmarCancelamentos} for verdadeiro;
     * nesse caso eles são cancelados e as contas de mesa abertas são encerradas sem cobrança.
     */
    @Transactional
    public FechamentoCaixaResponse fechar(Long funcionarioId, boolean confirmarCancelamentos) {
        Caixa caixa = abertura.caixaAberto().orElseThrow(() -> new NegocioException(HttpStatus.CONFLICT, "O caixa já está fechado"));
        List<Pedido> abertos = pedidos.findByEstadoNotIn(PedidoService.FINAIS);

        List<String> bloqueios = bloqueios(abertos);
        if (!bloqueios.isEmpty()) {
            throw new NegocioException("Não é possível fechar o caixa: resolva antes os pedidos abaixo", bloqueios);
        }
        if (!abertos.isEmpty() && !confirmarCancelamentos) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Há " + abertos.size() + (abertos.size() == 1 ? " pedido não concluído" : " pedidos não concluídos")
                            + ". Ao fechar o caixa eles serão cancelados. Confirme para continuar.",
                    abertos.stream().map(Pedido::getNumeroPedido).toList());
        }

        for (Pedido p : abertos) {
            var pendente = solicitacoes.findByPedidoIdAndEstado(p.getId(), EstadoCancelamento.PENDENTE);
            if (pendente.isPresent()) {
                cancelamento.decidir(pendente.get().getId(), true, MOTIVO_FECHAMENTO, funcionarioId);
            } else {
                estados.cancelar(p, funcionarioId, MOTIVO_FECHAMENTO);
            }
        }
        List<ContaMesa> contasAbertas = contas.findByEstadoOrderByAbertaEmDesc(EstadoContaMesa.ABERTA);
        for (ContaMesa c : contasAbertas) {
            mesas.encerrarSemCobranca(c.getId(), funcionarioId, MOTIVO_FECHAMENTO);
        }

        caixa.setFechadoEm(LocalDateTime.now());
        caixa.setFechadoPor(funcionarioId);
        caixa.setPedidosCancelados(abertos.size());
        caixas.save(caixa);
        return new FechamentoCaixaResponse(caixa.getFechadoEm(), abertos.size(), contasAbertas.size());
    }

    /** Pedidos que o sistema não cancela sozinho: pagamento já confirmado ou conta de mesa em fechamento. */
    private List<String> bloqueios(List<Pedido> abertos) {
        List<String> r = new ArrayList<>();
        for (Pedido p : abertos) {
            if (pagamentos.findByPedidoIdAndEstado(p.getId(), EstadoPagamento.CONFIRMADO).isPresent()) {
                r.add("Pedido " + p.getNumeroPedido() + ": pagamento já confirmado (conclua o pedido)");
            } else if (p.getContaMesaId() != null && contas.findById(p.getContaMesaId())
                    .map(c -> c.getEstado() != EstadoContaMesa.ABERTA).orElse(false)) {
                r.add("Pedido " + p.getNumeroPedido() + ": a conta de mesa está em fechamento (conclua o pagamento ou cancele o fechamento)");
            }
        }
        return r;
    }

    // ---------------------------------------------------------------- lançamento de pedidos

    @Transactional(readOnly = true)
    public List<ContaMesaResponse> contasDeMesaAbertas() {
        return mesas.listar(EstadoContaMesa.ABERTA);
    }

    @Transactional
    public ContaMesaResponse abrirContaDeMesa(AbrirMesaCaixaRequest req) {
        return mesas.abrirPeloCaixa(req.nome(), req.cpf());
    }

    @Transactional
    public PedidoCriadoResponse lancarPedido(PedidoCaixaRequest req, String chaveIdempotencia) {
        if (req.modalidade() == Modalidade.MESA) {
            if (req.codigoConta() == null || req.codigoConta().isBlank()) {
                throw new NegocioException("Informe a conta de mesa do pedido");
            }
            return mesas.pedidoPeloFuncionario(req.codigoConta(), new MesaPedidoRequest(null, req.itens(), req.observacao()), chaveIdempotencia);
        }
        return pedidoService.criarPeloCaixa(req, chaveIdempotencia);
    }
}
