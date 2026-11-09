package com.vilagourmet.service;

import com.vilagourmet.dto.PagamentoDTO.ConfirmarPagamentoRequest;
import com.vilagourmet.dto.PagamentoDTO.PagamentoResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.FechamentoMesa;
import com.vilagourmet.model.Pagamento;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.enums.EstadoFechamento;
import com.vilagourmet.model.enums.EstadoPagamento;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.model.enums.MetodoPagamento;
import com.vilagourmet.model.enums.Modalidade;
import com.vilagourmet.repository.FechamentoMesaRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagamentos manuais. O estado de pagamento é independente do estado operacional do pedido:
 * escolher um método registra apenas um pagamento PENDENTE; só a confirmação do funcionário o
 * torna recebido. Entrega/retirada pagam o pedido; mesa paga o fechamento da conta.
 * Pagamento integral e único por destino (sem parciais na V1).
 */
@Service
public class PagamentoService {

    private final PagamentoRepository pagamentos;
    private final PedidoRepository pedidos;
    private final FechamentoMesaRepository fechamentos;
    private final MesaService mesaService;
    private final PedidoEstadoService estadoService;

    public PagamentoService(PagamentoRepository pagamentos, PedidoRepository pedidos, FechamentoMesaRepository fechamentos,
                            MesaService mesaService, PedidoEstadoService estadoService) {
        this.pagamentos = pagamentos;
        this.pedidos = pedidos;
        this.fechamentos = fechamentos;
        this.mesaService = mesaService;
        this.estadoService = estadoService;
    }

    public static PagamentoResponse paraResponse(Pagamento p) {
        if (p == null) {
            return null;
        }
        BigDecimal troco = null;
        if (p.getMetodo() == MetodoPagamento.DINHEIRO && p.getValorRecebido() != null
                && p.getValorRecebido().compareTo(p.getValor()) > 0) {
            troco = p.getValorRecebido().subtract(p.getValor());
        }
        return new PagamentoResponse(p.getId(), p.getMetodo(), p.getEstado(), p.getValor(), p.getValorRecebido(), troco, p.getConfirmadoEm());
    }

    /** Registra a preferência de método do cliente (PENDENTE). Não significa que foi pago. */
    @Transactional
    public Pagamento registrarPendente(Pedido pedido, MetodoPagamento metodo) {
        Pagamento p = new Pagamento();
        p.setPedidoId(pedido.getId());
        p.setMetodo(metodo);
        p.setValor(pedido.getTotal());
        return pagamentos.save(p);
    }

    @Transactional
    public PagamentoResponse confirmarPedido(Long pedidoId, ConfirmarPagamentoRequest req, Long funcionarioId) {
        Pedido pedido = pedidos.findById(pedidoId).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado"));
        if (pedido.getModalidade() == Modalidade.MESA) {
            throw new NegocioException("Pedidos de mesa são pagos no fechamento da conta");
        }
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new NegocioException("Pedido cancelado não pode receber pagamento");
        }
        if (pagamentos.findByPedidoIdAndEstado(pedidoId, EstadoPagamento.CONFIRMADO).isPresent()) {
            throw new NegocioException(HttpStatus.CONFLICT, "O pagamento deste pedido já foi confirmado");
        }
        Pagamento p = pagamentos.findByPedidoIdAndEstado(pedidoId, EstadoPagamento.PENDENTE).orElseGet(() -> {
            Pagamento novo = new Pagamento();
            novo.setPedidoId(pedidoId);
            return novo;
        });
        p.setValor(pedido.getTotal());
        return PagamentoService.paraResponse(confirmar(p, req, funcionarioId));
    }

    @Transactional
    public PagamentoResponse confirmarFechamento(Long fechamentoId, ConfirmarPagamentoRequest req, Long funcionarioId) {
        FechamentoMesa f = fechamentos.findById(fechamentoId).orElseThrow(() -> new NaoEncontradoException("Fechamento não encontrado"));
        if (f.getEstado() != EstadoFechamento.PENDENTE) {
            throw new NegocioException(HttpStatus.CONFLICT, "O fechamento não está pendente de pagamento");
        }
        Pagamento p = pagamentos.findByFechamentoMesaIdAndEstado(fechamentoId, EstadoPagamento.PENDENTE).orElseGet(() -> {
            Pagamento novo = new Pagamento();
            novo.setFechamentoMesaId(fechamentoId);
            return novo;
        });
        p.setValor(f.getTotalFechamento());
        confirmar(p, req, funcionarioId);

        f.setEstado(EstadoFechamento.PAGO);
        f.setConcluidoEm(LocalDateTime.now());
        // pedidos servidos são concluídos com o pagamento; a conta é encerrada
        for (Pedido pedido : pedidos.findByContaMesaIdOrderByIdAsc(f.getContaMesaId())) {
            if (pedido.getEstado() == EstadoPedido.SERVIDO) {
                estadoService.aplicar(pedido, EstadoPedido.CONCLUIDO, funcionarioId, false, "Conta de mesa paga");
            }
        }
        mesaService.encerrar(f.getContaMesaId(), funcionarioId);
        return PagamentoService.paraResponse(p);
    }

    @Transactional(readOnly = true)
    public List<PagamentoResponse> listarPorPedido(Long pedidoId) {
        return pagamentos.findByPedidoIdOrderByIdDesc(pedidoId).stream().map(PagamentoService::paraResponse).toList();
    }

    private Pagamento confirmar(Pagamento p, ConfirmarPagamentoRequest req, Long funcionarioId) {
        if (req.metodo() == MetodoPagamento.DINHEIRO && req.valorRecebido() != null
                && req.valorRecebido().compareTo(p.getValor()) < 0) {
            throw new NegocioException("O valor recebido é menor que o total a pagar (pagamentos parciais não são aceitos)");
        }
        p.setMetodo(req.metodo());
        p.setValorRecebido(req.metodo() == MetodoPagamento.DINHEIRO ? req.valorRecebido() : null);
        p.setEstado(EstadoPagamento.CONFIRMADO);
        p.setConfirmadoPor(funcionarioId);
        p.setConfirmadoEm(LocalDateTime.now());
        return pagamentos.saveAndFlush(p);
    }
}
