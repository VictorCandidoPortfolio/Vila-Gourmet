package com.vilagourmet.service;

import com.vilagourmet.dto.PedidoDTO.PedidoResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.HistoricoEstadoPedido;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.enums.EstadoContaMesa;
import com.vilagourmet.model.enums.EstadoPagamento;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.model.enums.Modalidade;
import com.vilagourmet.repository.ContaMesaRepository;
import com.vilagourmet.repository.HistoricoEstadoPedidoRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.vilagourmet.model.enums.EstadoPedido.*;

/**
 * Máquina de estados dos pedidos. Só são aceitas as transições previstas para a modalidade, mais as
 * exceções explícitas (cancelamento e conclusão manual justificada). Toda mudança grava histórico na
 * mesma transação.
 */
@Service
public class PedidoEstadoService {

    static final Map<Modalidade, List<EstadoPedido>> FLUXO = Map.of(
            Modalidade.ENTREGA, List.of(AGUARDANDO_CONFIRMACAO, CONFIRMADO, EM_PREPARACAO, A_CAMINHO, CONCLUIDO),
            Modalidade.RETIRADA, List.of(AGUARDANDO_CONFIRMACAO, CONFIRMADO, EM_PREPARACAO, PRONTO_PARA_RETIRADA, CONCLUIDO),
            Modalidade.MESA, List.of(AGUARDANDO_CONFIRMACAO, CONFIRMADO, EM_PREPARACAO, SERVIDO, CONCLUIDO));

    private final PedidoRepository pedidos;
    private final HistoricoEstadoPedidoRepository historicos;
    private final PagamentoRepository pagamentos;
    private final ContaMesaRepository contas;
    private final CodigoService codigos;
    private final PedidoMapper mapper;

    public PedidoEstadoService(PedidoRepository pedidos, HistoricoEstadoPedidoRepository historicos,
                               PagamentoRepository pagamentos, ContaMesaRepository contas, CodigoService codigos,
                               PedidoMapper mapper) {
        this.pedidos = pedidos;
        this.historicos = historicos;
        this.pagamentos = pagamentos;
        this.contas = contas;
        this.codigos = codigos;
        this.mapper = mapper;
    }

    @Transactional
    public PedidoResponse transicionar(Long pedidoId, EstadoPedido novo, Long funcionarioId) {
        Pedido p = obter(pedidoId);
        if (novo == CANCELADO) {
            throw new NegocioException("Para cancelar um pedido use o fluxo de cancelamento");
        }
        if (novo == CONCLUIDO && p.getModalidade() != Modalidade.MESA) {
            throw new NegocioException("Conclua entregas e retiradas com o código do cliente ou pela conclusão manual justificada");
        }
        exigirProximo(p, novo);
        aplicar(p, novo, funcionarioId, false, null);
        return mapper.toResponse(p, true);
    }

    @Transactional(noRollbackFor = NegocioException.class)
    public PedidoResponse concluirComCodigo(Long pedidoId, String codigo, Long funcionarioId) {
        Pedido p = obter(pedidoId);
        exigirConclusaoEntregaRetirada(p);
        codigos.validarEConsumirConfirmacao(pedidoId, codigo);
        aplicar(p, CONCLUIDO, funcionarioId, false, "Confirmado pelo cliente com código");
        return mapper.toResponse(p, true);
    }

    /** Conclusão sem o código do cliente: exige justificativa e fica auditada como manual. */
    @Transactional
    public PedidoResponse concluirManual(Long pedidoId, String justificativa, Long funcionarioId) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new NegocioException("A justificativa é obrigatória na conclusão manual");
        }
        Pedido p = obter(pedidoId);
        exigirConclusaoEntregaRetirada(p);
        aplicar(p, CONCLUIDO, funcionarioId, true, justificativa.trim());
        return mapper.toResponse(p, true);
    }

    /** Cancela efetivamente o pedido (usado após aprovação ou por funcionário autorizado). */
    @Transactional
    public void cancelar(Pedido p, Long funcionarioId, String justificativa) {
        if (p.getEstado() == CONCLUIDO || p.getEstado() == CANCELADO) {
            throw new NegocioException("O pedido já foi finalizado e não pode ser cancelado");
        }
        if (pagamentos.findByPedidoIdAndEstado(p.getId(), EstadoPagamento.CONFIRMADO).isPresent()) {
            throw new NegocioException("Pedido com pagamento confirmado não pode ser cancelado (estorno fora do escopo da V1)");
        }
        if (p.getContaMesaId() != null && contas.findById(p.getContaMesaId())
                .map(c -> c.getEstado() != EstadoContaMesa.ABERTA).orElse(false)) {
            throw new NegocioException("Pedido de mesa não pode ser cancelado com a conta em fechamento");
        }
        aplicar(p, CANCELADO, funcionarioId, false, justificativa);
    }

    /** Aplica o novo estado e grava o histórico. Não valida a transição: valide antes. */
    @Transactional
    public void aplicar(Pedido p, EstadoPedido novo, Long funcionarioId, boolean manual, String justificativa) {
        EstadoPedido anterior = p.getEstado();
        p.setEstado(novo);
        p.setAtualizadoEm(LocalDateTime.now());
        if (novo == CONFIRMADO && p.getConfirmadoEm() == null) {
            p.setConfirmadoEm(LocalDateTime.now());
        }
        pedidos.save(p);
        registrarHistorico(p.getId(), anterior, novo, funcionarioId, manual, justificativa);
    }

    @Transactional
    public void registrarHistorico(Long pedidoId, EstadoPedido anterior, EstadoPedido novo, Long funcionarioId,
                                   boolean manual, String justificativa) {
        HistoricoEstadoPedido h = new HistoricoEstadoPedido();
        h.setPedidoId(pedidoId);
        h.setEstadoAnterior(anterior == null ? null : anterior.name());
        h.setEstadoNovo(novo.name());
        h.setFuncionarioId(funcionarioId);
        h.setConclusaoManual(manual);
        h.setJustificativa(justificativa);
        historicos.save(h);
    }

    private void exigirProximo(Pedido p, EstadoPedido novo) {
        if (p.getEstado() == CONCLUIDO || p.getEstado() == CANCELADO) {
            throw new NegocioException("O pedido já está finalizado (" + p.getEstado() + ")");
        }
        List<EstadoPedido> fluxo = FLUXO.get(p.getModalidade());
        int i = fluxo.indexOf(p.getEstado());
        if (i < 0 || i + 1 >= fluxo.size() || fluxo.get(i + 1) != novo) {
            throw new NegocioException("Transição inválida para pedido de " + p.getModalidade()
                    + ": " + p.getEstado() + " → " + novo);
        }
    }

    private void exigirConclusaoEntregaRetirada(Pedido p) {
        if (p.getModalidade() == Modalidade.MESA) {
            throw new NegocioException("Pedidos de mesa são concluídos pelo fechamento da conta");
        }
        EstadoPedido ultimoAntesDeConcluir = p.getModalidade() == Modalidade.ENTREGA ? A_CAMINHO : PRONTO_PARA_RETIRADA;
        if (p.getEstado() != ultimoAntesDeConcluir) {
            throw new NegocioException("Só é possível concluir o pedido no estado " + ultimoAntesDeConcluir
                    + " (estado atual: " + p.getEstado() + ")");
        }
    }

    private Pedido obter(Long id) {
        return pedidos.findById(id).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado"));
    }
}
