package com.vilagourmet.service;

import com.vilagourmet.dto.PedidoDTO.CancelamentoResponse;
import com.vilagourmet.dto.PedidoDTO.RastreamentoRequest;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.SolicitacaoCancelamento;
import com.vilagourmet.model.enums.EstadoCancelamento;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.model.enums.TipoSolicitante;
import com.vilagourmet.repository.PedidoRepository;
import com.vilagourmet.repository.SolicitacaoCancelamentoRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancelamento. Solicitar não é cancelar: antes da confirmação do restaurante o pedido é cancelado
 * imediatamente (sem aprovação); depois, a solicitação fica PENDENTE até a decisão do restaurante.
 */
@Service
public class CancelamentoService {

    private final SolicitacaoCancelamentoRepository solicitacoes;
    private final PedidoRepository pedidos;
    private final PedidoEstadoService estados;
    private final RastreamentoService rastreamento;

    public CancelamentoService(SolicitacaoCancelamentoRepository solicitacoes, PedidoRepository pedidos,
                               PedidoEstadoService estados, RastreamentoService rastreamento) {
        this.solicitacoes = solicitacoes;
        this.pedidos = pedidos;
        this.estados = estados;
        this.rastreamento = rastreamento;
    }

    @Transactional
    public CancelamentoResponse solicitarPorCliente(Long pedidoId, Long clienteId) {
        Pedido p = pedidos.findById(pedidoId).filter(x -> clienteId.equals(x.getClienteId()))
                .orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado"));
        return solicitar(p, TipoSolicitante.CLIENTE, clienteId, null);
    }

    /** Visitante identificado por código de acompanhamento ou número + CPF. */
    @Transactional
    public CancelamentoResponse solicitarPublico(RastreamentoRequest req, String ip) {
        Pedido p = rastreamento.localizar(req, ip);
        return solicitar(p, p.getClienteId() == null ? TipoSolicitante.VISITANTE : TipoSolicitante.CLIENTE, p.getClienteId(), null);
    }

    @Transactional
    public CancelamentoResponse solicitarPorFuncionario(Long pedidoId, Long funcionarioId, String justificativa) {
        Pedido p = pedidos.findById(pedidoId).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado"));
        CancelamentoResponse r = solicitar(p, TipoSolicitante.FUNCIONARIO, null, funcionarioId);
        // funcionário autorizado cancela diretamente, ainda que a solicitação tenha ficado pendente
        if (r.estado() == EstadoCancelamento.PENDENTE) {
            return decidir(r.id(), true, justificativa, funcionarioId);
        }
        return r;
    }

    @Transactional
    public CancelamentoResponse decidir(Long id, boolean aprovar, String justificativa, Long funcionarioId) {
        SolicitacaoCancelamento s = solicitacoes.findById(id).orElseThrow(() -> new NaoEncontradoException("Solicitação não encontrada"));
        if (s.getEstado() != EstadoCancelamento.PENDENTE) {
            throw new NegocioException(HttpStatus.CONFLICT, "A solicitação já foi decidida");
        }
        if (!aprovar && (justificativa == null || justificativa.isBlank())) {
            throw new NegocioException("Informe a justificativa da rejeição");
        }
        Pedido p = pedidos.findById(s.getPedidoId()).orElseThrow();
        if (aprovar) {
            estados.cancelar(p, funcionarioId, justificativa);
        }
        s.setEstado(aprovar ? EstadoCancelamento.APROVADO : EstadoCancelamento.REJEITADO);
        s.setDecididoPor(funcionarioId);
        s.setDecididoEm(LocalDateTime.now());
        s.setJustificativa(justificativa);
        return paraResponse(s, p);
    }

    @Transactional(readOnly = true)
    public List<CancelamentoResponse> listar(EstadoCancelamento estado) {
        List<SolicitacaoCancelamento> lista = estado == null ? solicitacoes.findAllByOrderBySolicitadoEmDesc()
                : solicitacoes.findByEstadoOrderBySolicitadoEmAsc(estado);
        return lista.stream().map(s -> paraResponse(s, pedidos.findById(s.getPedidoId()).orElseThrow())).toList();
    }

    private CancelamentoResponse solicitar(Pedido p, TipoSolicitante tipo, Long clienteId, Long funcionarioId) {
        if (p.getEstado() == EstadoPedido.CONCLUIDO || p.getEstado() == EstadoPedido.CANCELADO) {
            throw new NegocioException("O pedido já foi finalizado");
        }
        if (solicitacoes.findByPedidoIdAndEstado(p.getId(), EstadoCancelamento.PENDENTE).isPresent()) {
            throw new NegocioException(HttpStatus.CONFLICT, "Já existe uma solicitação de cancelamento pendente");
        }
        SolicitacaoCancelamento s = new SolicitacaoCancelamento();
        s.setPedidoId(p.getId());
        s.setSolicitadoPorTipo(tipo);
        s.setClienteId(clienteId);
        s.setSolicitadoPorFuncionarioId(funcionarioId);
        if (p.getEstado() == EstadoPedido.AGUARDANDO_CONFIRMACAO) {
            String motivo = "Cancelado antes da confirmação do restaurante";
            estados.cancelar(p, funcionarioId, motivo);
            s.setEstado(EstadoCancelamento.APROVADO);
            s.setDecididoEm(LocalDateTime.now());
            s.setJustificativa(motivo);
        }
        return paraResponse(solicitacoes.save(s), p);
    }

    private CancelamentoResponse paraResponse(SolicitacaoCancelamento s, Pedido p) {
        return new CancelamentoResponse(s.getId(), s.getPedidoId(), p.getNumeroPedido(), s.getSolicitadoPorTipo(),
                s.getEstado(), s.getSolicitadoEm(), s.getDecididoEm(), s.getJustificativa(), p.getEstado());
    }
}
