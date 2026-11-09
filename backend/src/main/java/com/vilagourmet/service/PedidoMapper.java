package com.vilagourmet.service;

import com.vilagourmet.dto.PedidoDTO.AdicionalResponse;
import com.vilagourmet.dto.PedidoDTO.EnderecoSnapshot;
import com.vilagourmet.dto.PedidoDTO.EscolhaResponse;
import com.vilagourmet.dto.PedidoDTO.HistoricoResponse;
import com.vilagourmet.dto.PedidoDTO.ItemResponse;
import com.vilagourmet.dto.PedidoDTO.PedidoResponse;
import com.vilagourmet.model.ItemPedido;
import com.vilagourmet.model.Pagamento;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.enums.EstadoPagamento;
import com.vilagourmet.repository.ContaMesaRepository;
import com.vilagourmet.repository.HistoricoEstadoPedidoRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.util.Cpf;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converte Pedido em DTO de resposta. CPF sempre mascarado; deve ser chamado dentro de transação. */
@Component
public class PedidoMapper {

    private final PagamentoRepository pagamentos;
    private final HistoricoEstadoPedidoRepository historicos;
    private final ContaMesaRepository contas;

    public PedidoMapper(PagamentoRepository pagamentos, HistoricoEstadoPedidoRepository historicos, ContaMesaRepository contas) {
        this.pagamentos = pagamentos;
        this.historicos = historicos;
        this.contas = contas;
    }

    public PedidoResponse toResponse(Pedido p, boolean comHistorico) {
        EnderecoSnapshot endereco = p.getEnderecoLogradouro() == null ? null
                : new EnderecoSnapshot(p.getEnderecoCep(), p.getEnderecoLogradouro(), p.getEnderecoNumero(),
                        p.getEnderecoComplemento(), p.getEnderecoBairro(), p.getEnderecoCidade(), p.getEnderecoEstado());
        List<ItemResponse> itens = p.getItens().stream().map(this::item).toList();
        List<HistoricoResponse> historico = comHistorico
                ? historicos.findByPedidoIdOrderByIdAsc(p.getId()).stream()
                        .map(h -> new HistoricoResponse(h.getEstadoAnterior(), h.getEstadoNovo(), h.isConclusaoManual(),
                                h.getJustificativa(), h.getFuncionarioId(), h.getCriadoEm())).toList()
                : null;
        String codigoConta = p.getContaMesaId() == null ? null
                : contas.findById(p.getContaMesaId()).map(c -> c.getCodigoConta()).orElse(null);
        return new PedidoResponse(p.getId(), p.getNumeroPedido(), p.getModalidade(), p.getEstado(), p.getNomeResponsavel(),
                Cpf.mascarar(p.getCpfResponsavel()), endereco, p.getSubtotal(), p.getTaxaEntrega(), p.getTotal(),
                p.getObservacao(), p.getCriadoEm(), codigoConta, itens, pagamentoDe(p), historico);
    }

    /** Pagamento confirmado, se houver; senão o mais recente. */
    private com.vilagourmet.dto.PagamentoDTO.PagamentoResponse pagamentoDe(Pedido p) {
        List<Pagamento> lista = pagamentos.findByPedidoIdOrderByIdDesc(p.getId());
        Pagamento escolhido = lista.stream().filter(x -> x.getEstado() == EstadoPagamento.CONFIRMADO).findFirst()
                .orElse(lista.isEmpty() ? null : lista.get(0));
        return PagamentoService.paraResponse(escolhido);
    }

    private ItemResponse item(ItemPedido i) {
        return new ItemResponse(i.getNomeProdutoHistorico(), i.getTipoProdutoHistorico(), i.getQuantidade(),
                i.getPrecoUnitario(), i.getSubtotal(),
                i.getEscolhas().stream().map(e -> new EscolhaResponse(e.getNomeGrupoHistorico(), e.getNomeOpcaoHistorico(),
                        e.getModalidadeCobrancaHistorica(), e.getQuantidade(), e.getPrecoUnitarioAplicado(), e.getSubtotal())).toList(),
                i.getAdicionais().stream().map(a -> new AdicionalResponse(a.getNomeHistorico(), a.getQuantidade(),
                        a.getPrecoUnitarioAplicado(), a.getSubtotal())).toList());
    }
}
