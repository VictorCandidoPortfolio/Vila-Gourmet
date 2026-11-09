package com.vilagourmet.service;

import com.vilagourmet.dto.CotacaoDTO.ItemCotado;
import com.vilagourmet.dto.CotacaoDTO.LinhaAdicional;
import com.vilagourmet.dto.CotacaoDTO.LinhaEscolha;
import com.vilagourmet.dto.CotacaoDTO.SelecaoAdicional;
import com.vilagourmet.dto.CotacaoDTO.SelecaoOpcao;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.AdicionalSimples;
import com.vilagourmet.model.ConfiguracaoPrecificacao;
import com.vilagourmet.model.GrupoOpcao;
import com.vilagourmet.model.Opcao;
import com.vilagourmet.model.Produto;
import com.vilagourmet.model.enums.EstrategiaPrecificacao;
import com.vilagourmet.model.enums.ModalidadeCobranca;
import com.vilagourmet.model.enums.StatusProduto;
import com.vilagourmet.model.enums.TipoProduto;
import com.vilagourmet.util.Dinheiro;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Motor de precificação. Função pura sobre o catálogo carregado: valida as seleções contra os
 * limites de grupo e de opção e calcula o preço pela estratégia do produto, com BigDecimal e
 * arredondamento final HALF_UP em duas casas.
 *
 * <p>Interpretação dos limites: grupo com mínimo &gt; 0 é obrigatório; grupo com mínimo 0 é opcional e,
 * se nenhuma opção dele for escolhida, os mínimos das opções não se aplicam. Se o grupo for usado,
 * o mínimo e o máximo de cada opção disponível também valem (simultaneamente aos do grupo).
 */
@Service
public class PrecificacaoService {

    public record GrupoCatalogo(GrupoOpcao grupo, List<Opcao> opcoes) {
    }

    public record CatalogoProduto(Produto produto, ConfiguracaoPrecificacao config,
                                  List<GrupoCatalogo> grupos, List<AdicionalSimples> adicionais) {
    }

    public ItemCotado calcular(CatalogoProduto cat, int quantidade, List<SelecaoOpcao> escolhas,
                               List<SelecaoAdicional> adicionais) {
        Produto p = cat.produto();
        if (quantidade < 1) {
            throw new NegocioException("A quantidade deve ser maior que zero");
        }
        if (p.getStatus() != StatusProduto.PUBLICADO || !p.isDisponivel()) {
            throw new NegocioException("Produto indisponível: " + p.getNome());
        }
        List<SelecaoOpcao> escs = escolhas == null ? List.of() : escolhas;
        List<SelecaoAdicional> ads = adicionais == null ? List.of() : adicionais;
        return p.getTipo() == TipoProduto.SIMPLES
                ? calcularSimples(cat, quantidade, escs, ads)
                : calcularComposto(cat, quantidade, escs, ads);
    }

    private ItemCotado calcularSimples(CatalogoProduto cat, int quantidade, List<SelecaoOpcao> escolhas,
                                       List<SelecaoAdicional> adicionais) {
        Produto p = cat.produto();
        if (!escolhas.isEmpty()) {
            throw new NegocioException("Produto simples não possui grupos de opções");
        }
        Map<Long, AdicionalSimples> porId = new LinkedHashMap<>();
        cat.adicionais().forEach(a -> porId.put(a.getId(), a));

        BigDecimal unitario = p.getPrecoBase();
        List<LinhaAdicional> linhas = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : agrupar(adicionais.stream().map(s -> Map.entry(s.adicionalId(), s.quantidade())).toList()).entrySet()) {
            AdicionalSimples a = porId.get(e.getKey());
            if (a == null) {
                throw new NegocioException("Adicional inválido para o produto " + p.getNome());
            }
            if (!a.isDisponivel()) {
                throw new NegocioException("Adicional indisponível: " + a.getNome());
            }
            BigDecimal sub = Dinheiro.arredondar(a.getPreco().multiply(BigDecimal.valueOf(e.getValue())));
            linhas.add(new LinhaAdicional(a.getId(), a.getNome(), e.getValue(), Dinheiro.arredondar(a.getPreco()), sub));
            unitario = unitario.add(sub);
        }
        unitario = Dinheiro.arredondar(unitario);
        return new ItemCotado(p.getId(), p.getNome(), TipoProduto.SIMPLES, p.getPrecoBase(), null, null, quantidade,
                unitario, Dinheiro.arredondar(unitario.multiply(BigDecimal.valueOf(quantidade))), List.of(), linhas);
    }

    private ItemCotado calcularComposto(CatalogoProduto cat, int quantidade, List<SelecaoOpcao> escolhas,
                                        List<SelecaoAdicional> adicionais) {
        Produto p = cat.produto();
        ConfiguracaoPrecificacao cfg = cat.config();
        if (cfg == null) {
            throw new NegocioException("Produto sem estratégia de precificação configurada: " + p.getNome());
        }
        if (!adicionais.isEmpty()) {
            throw new NegocioException("Adicionais simples só se aplicam a produtos simples");
        }

        Map<Long, Integer> qtdPorOpcao = agrupar(escolhas.stream().map(s -> Map.entry(s.opcaoId(), s.quantidade())).toList());
        Map<Long, GrupoOpcao> grupoDaOpcao = new LinkedHashMap<>();
        for (GrupoCatalogo g : cat.grupos()) {
            if (g.grupo().isAtivo()) {
                g.opcoes().forEach(o -> grupoDaOpcao.put(o.getId(), g.grupo()));
            }
        }
        for (Long opcaoId : qtdPorOpcao.keySet()) {
            if (!grupoDaOpcao.containsKey(opcaoId)) {
                throw new NegocioException("Opção inválida para o produto " + p.getNome());
            }
        }

        List<LinhaEscolha> linhas = new ArrayList<>();
        List<BigDecimal> valoresPrincipais = new ArrayList<>();
        BigDecimal separado = BigDecimal.ZERO;
        boolean fixo = cfg.getEstrategia() == EstrategiaPrecificacao.FIXO;

        for (GrupoCatalogo gc : cat.grupos()) {
            GrupoOpcao g = gc.grupo();
            if (!g.isAtivo()) {
                continue;
            }
            int total = gc.opcoes().stream().mapToInt(o -> qtdPorOpcao.getOrDefault(o.getId(), 0)).sum();
            if (total == 0) {
                if (g.getMinimo() > 0) {
                    throw new NegocioException("O grupo '" + g.getNome() + "' exige ao menos " + g.getMinimo() + " seleção(ões)");
                }
                continue;
            }
            if (total < g.getMinimo() || total > g.getMaximo()) {
                throw new NegocioException("O grupo '" + g.getNome() + "' aceita entre " + g.getMinimo() + " e "
                        + g.getMaximo() + " seleção(ões); informado: " + total);
            }
            for (Opcao o : gc.opcoes()) {
                int q = qtdPorOpcao.getOrDefault(o.getId(), 0);
                if (q > 0 && !o.isDisponivel()) {
                    throw new NegocioException("Opção indisponível: " + o.getNome());
                }
                if (q > o.getMaximo()) {
                    throw new NegocioException("A opção '" + o.getNome() + "' aceita no máximo " + o.getMaximo() + " seleção(ões)");
                }
                if (o.isDisponivel() && q < o.getMinimo()) {
                    throw new NegocioException("A opção '" + o.getNome() + "' exige ao menos " + o.getMinimo() + " seleção(ões)");
                }
                if (q == 0) {
                    continue;
                }
                BigDecimal aplicado = switch (g.getModalidadeCobranca()) {
                    case GRATUITA -> BigDecimal.ZERO;
                    case PRINCIPAL -> fixo ? BigDecimal.ZERO : o.getPreco();
                    case SEPARADA -> o.getPreco();
                };
                if (g.getModalidadeCobranca() == ModalidadeCobranca.PRINCIPAL && !fixo) {
                    for (int i = 0; i < q; i++) {
                        valoresPrincipais.add(o.getPreco());
                    }
                }
                if (g.getModalidadeCobranca() == ModalidadeCobranca.SEPARADA) {
                    separado = separado.add(o.getPreco().multiply(BigDecimal.valueOf(q)));
                }
                linhas.add(new LinhaEscolha(g.getId(), o.getId(), g.getNome(), o.getNome(), g.getModalidadeCobranca(), q,
                        Dinheiro.arredondar(aplicado), Dinheiro.arredondar(aplicado.multiply(BigDecimal.valueOf(q)))));
            }
        }

        BigDecimal principal = precoPrincipal(cfg, p, valoresPrincipais);
        BigDecimal unitario = Dinheiro.arredondar(principal.add(separado));
        return new ItemCotado(p.getId(), p.getNome(), TipoProduto.COMPOSTO, p.getPrecoBase(), cfg.getEstrategia(),
                cfg.isParticipaPrecoBase(), quantidade, unitario,
                Dinheiro.arredondar(unitario.multiply(BigDecimal.valueOf(quantidade))), linhas, List.of());
    }

    private BigDecimal precoPrincipal(ConfiguracaoPrecificacao cfg, Produto p, List<BigDecimal> principais) {
        if (cfg.getEstrategia() == EstrategiaPrecificacao.FIXO) {
            if (p.getPrecoBase() == null) {
                throw new NegocioException("Produto com estratégia FIXO sem preço-base: " + p.getNome());
            }
            return p.getPrecoBase();
        }
        if (principais.isEmpty()) {
            throw new NegocioException("Selecione ao menos uma opção que participa do cálculo principal do preço");
        }
        List<BigDecimal> valores = new ArrayList<>(principais);
        if (cfg.isParticipaPrecoBase()) {
            if (p.getPrecoBase() == null) {
                throw new NegocioException("Produto configurado para incluir o preço-base, mas sem preço-base: " + p.getNome());
            }
            valores.add(p.getPrecoBase());
        }
        return switch (cfg.getEstrategia()) {
            case SOMA -> valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case MEDIA -> valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(valores.size()), 10, RoundingMode.HALF_UP); // média simples, sem ponderação
            case MAIOR_VALOR -> valores.stream().max(Comparator.naturalOrder()).orElseThrow();
            case FIXO -> throw new IllegalStateException();
        };
    }

    private Map<Long, Integer> agrupar(List<Map.Entry<Long, Integer>> selecoes) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> s : selecoes) {
            if (s.getValue() < 1) {
                throw new NegocioException("A quantidade de cada seleção deve ser maior que zero");
            }
            out.merge(s.getKey(), s.getValue(), Integer::sum);
        }
        return out;
    }
}
