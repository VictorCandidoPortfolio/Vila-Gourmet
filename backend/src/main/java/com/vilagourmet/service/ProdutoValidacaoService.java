package com.vilagourmet.service;

import com.vilagourmet.model.ConfiguracaoPrecificacao;
import com.vilagourmet.model.GrupoOpcao;
import com.vilagourmet.model.Opcao;
import com.vilagourmet.model.Produto;
import com.vilagourmet.model.enums.EstrategiaPrecificacao;
import com.vilagourmet.model.enums.ModalidadeCobranca;
import com.vilagourmet.model.enums.TipoProduto;
import com.vilagourmet.repository.ConfiguracaoPrecificacaoRepository;
import com.vilagourmet.repository.GrupoOpcaoRepository;
import com.vilagourmet.repository.OpcaoRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida a configuração de um produto antes da publicação. Para produtos compostos, rejeita
 * qualquer combinação de limites que torne impossível montar o produto ou que possa gerar
 * preço principal inválido, explicando cada conflito.
 */
@Service
public class ProdutoValidacaoService {

    private final ConfiguracaoPrecificacaoRepository configs;
    private final GrupoOpcaoRepository grupos;
    private final OpcaoRepository opcoes;

    public ProdutoValidacaoService(ConfiguracaoPrecificacaoRepository configs, GrupoOpcaoRepository grupos,
                                   OpcaoRepository opcoes) {
        this.configs = configs;
        this.grupos = grupos;
        this.opcoes = opcoes;
    }

    @Transactional(readOnly = true)
    public List<String> validar(Produto produto) {
        List<String> problemas = new ArrayList<>();
        if (produto.getTipo() == TipoProduto.SIMPLES) {
            if (produto.getPrecoBase() == null) {
                problemas.add("Produto simples exige preço de venda");
            }
            return problemas;
        }

        ConfiguracaoPrecificacao cfg = configs.findById(produto.getId()).orElse(null);
        if (cfg == null) {
            problemas.add("Defina a estratégia de precificação");
            return problemas;
        }
        boolean fixo = cfg.getEstrategia() == EstrategiaPrecificacao.FIXO;
        if ((fixo || cfg.isParticipaPrecoBase()) && produto.getPrecoBase() == null) {
            problemas.add(fixo ? "A estratégia FIXO exige o preço-base"
                    : "O preço-base está configurado para participar do cálculo, mas não foi informado");
        }

        List<GrupoOpcao> ativos = grupos.findByProdutoIdOrderByIdAsc(produto.getId()).stream().filter(GrupoOpcao::isAtivo).toList();
        if (ativos.isEmpty()) {
            problemas.add("Produto composto precisa de ao menos um grupo de opções ativo");
        }
        boolean temPrincipalObrigatorio = false;
        for (GrupoOpcao g : ativos) {
            String rotulo = "Grupo '" + g.getNome() + "': ";
            List<Opcao> disponiveis = opcoes.findByGrupoIdOrderByIdAsc(g.getId()).stream().filter(Opcao::isDisponivel).toList();
            if (disponiveis.isEmpty()) {
                problemas.add(rotulo + "não possui opções disponíveis");
                continue;
            }
            if (g.getMaximo() < 1) {
                problemas.add(rotulo + "o máximo deve ser pelo menos 1");
            }
            long somaMaximos = disponiveis.stream().mapToLong(Opcao::getMaximo).sum();
            long somaMinimos = disponiveis.stream().mapToLong(Opcao::getMinimo).sum();
            if (g.getMinimo() > somaMaximos) {
                problemas.add(rotulo + "o mínimo do grupo (" + g.getMinimo() + ") é maior que a soma dos máximos das opções (" + somaMaximos + ")");
            }
            if (somaMinimos > g.getMaximo()) {
                problemas.add(rotulo + "a soma dos mínimos das opções (" + somaMinimos + ") é maior que o máximo do grupo (" + g.getMaximo() + ")");
            }
            disponiveis.stream().filter(o -> o.getMinimo() > g.getMaximo()).forEach(o ->
                    problemas.add(rotulo + "o mínimo da opção '" + o.getNome() + "' excede o máximo do grupo"));
            if (g.getModalidadeCobranca() == ModalidadeCobranca.PRINCIPAL) {
                if (fixo) {
                    problemas.add(rotulo + "a estratégia FIXO não usa grupos participantes do cálculo principal (use cobrança separada ou gratuita)");
                } else if (g.getMinimo() >= 1) {
                    temPrincipalObrigatorio = true;
                }
            }
        }
        if (!fixo && !temPrincipalObrigatorio) {
            problemas.add("A estratégia " + cfg.getEstrategia() + " exige ao menos um grupo ativo de cobrança PRINCIPAL com mínimo >= 1, "
                    + "para que o preço principal nunca fique sem valores participantes");
        }
        return problemas;
    }
}
