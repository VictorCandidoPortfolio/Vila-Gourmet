package com.vilagourmet.service;

import com.vilagourmet.dto.PedidoDTO.HistoricoPublico;
import com.vilagourmet.dto.PedidoDTO.RastreamentoRequest;
import com.vilagourmet.dto.PedidoDTO.RastreamentoResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Pagamento;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.enums.EstadoPagamento;
import com.vilagourmet.repository.HistoricoEstadoPedidoRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import com.vilagourmet.util.Cpf;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rastreamento público: código secreto de acompanhamento OU número do pedido + CPF informado na compra.
 * A resposta não expõe CPF, endereço nem o código de confirmação. Tentativas que não localizam
 * pedido são limitadas por IP para impedir enumeração.
 */
@Service
public class RastreamentoService {

    private final PedidoRepository pedidos;
    private final HistoricoEstadoPedidoRepository historicos;
    private final PagamentoRepository pagamentos;
    private final CodigoService codigos;
    private final RateLimiterService limiter;

    public RastreamentoService(PedidoRepository pedidos, HistoricoEstadoPedidoRepository historicos,
                               PagamentoRepository pagamentos, CodigoService codigos, RateLimiterService limiter) {
        this.pedidos = pedidos;
        this.historicos = historicos;
        this.pagamentos = pagamentos;
        this.codigos = codigos;
        this.limiter = limiter;
    }

    /** Localiza o pedido pela identificação pública; resposta genérica em qualquer falha. */
    @Transactional(readOnly = true)
    public Pedido localizar(RastreamentoRequest req, String ip) {
        String chave = "rastreio:" + ip;
        limiter.exigirDisponivel(chave);
        Optional<Pedido> pedido;
        if (req.codigo() != null && !req.codigo().isBlank()) {
            pedido = codigos.localizarPedidoPorAcompanhamento(req.codigo()).flatMap(pedidos::findById);
        } else if (req.numeroPedido() != null && !req.numeroPedido().isBlank() && req.cpf() != null && !req.cpf().isBlank()) {
            String cpf = Cpf.normalizar(req.cpf());
            pedido = pedidos.findByNumeroPedido(req.numeroPedido().trim().toUpperCase())
                    .filter(p -> iguais(p.getCpfResponsavel(), cpf));
        } else {
            throw new NegocioException("Informe o código de acompanhamento ou o número do pedido e o CPF");
        }
        if (pedido.isEmpty()) {
            limiter.registrarFalha(chave);
            throw new NaoEncontradoException("Pedido não encontrado");
        }
        return pedido.get();
    }

    @Transactional(readOnly = true)
    public RastreamentoResponse consultar(RastreamentoRequest req, String ip) {
        Pedido p = localizar(req, ip);
        List<Pagamento> lista = pagamentos.findByPedidoIdOrderByIdDesc(p.getId());
        String pagamento = lista.stream().anyMatch(x -> x.getEstado() == EstadoPagamento.CONFIRMADO) ? "PAGO" : "PENDENTE";
        return new RastreamentoResponse(p.getNumeroPedido(), p.getModalidade(), p.getEstado(), p.getTotal(), pagamento,
                p.getItens().stream().map(i -> i.getQuantidade() + "x " + i.getNomeProdutoHistorico()).toList(),
                historicos.findByPedidoIdOrderByIdAsc(p.getId()).stream()
                        .map(h -> new HistoricoPublico(h.getEstadoNovo(), h.getCriadoEm())).toList());
    }

    private static boolean iguais(String a, String b) {
        return a != null && b != null
                && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
