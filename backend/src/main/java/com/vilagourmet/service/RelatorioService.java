package com.vilagourmet.service;

import com.vilagourmet.dto.RelatorioDTO.RelatorioOperacionalResponse;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.enums.EstadoPagamento;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import com.vilagourmet.util.Dinheiro;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Relatórios operacionais básicos da V1: quantidade, distribuição por estado/modalidade e total vendido. */
@Service
public class RelatorioService {

    private final PedidoRepository pedidos;
    private final PagamentoRepository pagamentos;

    public RelatorioService(PedidoRepository pedidos, PagamentoRepository pagamentos) {
        this.pedidos = pedidos;
        this.pagamentos = pagamentos;
    }

    @Transactional(readOnly = true)
    public RelatorioOperacionalResponse operacional(LocalDate inicio, LocalDate fim) {
        LocalDate f = fim != null ? fim : LocalDate.now();
        LocalDate i = inicio != null ? inicio : f.minusDays(29);
        if (i.isAfter(f)) {
            throw new NegocioException("A data inicial não pode ser posterior à data final");
        }
        LocalDateTime de = i.atStartOfDay();
        LocalDateTime ate = f.plusDays(1).atStartOfDay();
        return new RelatorioOperacionalResponse(i, f, pedidos.contar(de, ate),
                paraMapa(pedidos.contarPorEstado(de, ate)), paraMapa(pedidos.contarPorModalidade(de, ate)),
                Dinheiro.arredondar(pedidos.totalVendido(de, ate,
                        List.of(EstadoPedido.CANCELADO, EstadoPedido.AGUARDANDO_CONFIRMACAO))),
                Dinheiro.arredondar(pagamentos.totalPorEstado(EstadoPagamento.CONFIRMADO, de, ate)));
    }

    private Map<String, Long> paraMapa(List<Object[]> linhas) {
        Map<String, Long> mapa = new LinkedHashMap<>();
        for (Object[] l : linhas) {
            mapa.put(l[0].toString(), ((Number) l[1]).longValue());
        }
        return mapa;
    }
}
