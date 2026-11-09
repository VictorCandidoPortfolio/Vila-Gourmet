package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProdutoValidacaoServiceTest {

    private ConfiguracaoPrecificacaoRepository configs;
    private GrupoOpcaoRepository grupos;
    private OpcaoRepository opcoes;
    private ProdutoValidacaoService servico;
    private Produto produto;

    @BeforeEach
    void preparar() {
        configs = mock(ConfiguracaoPrecificacaoRepository.class);
        grupos = mock(GrupoOpcaoRepository.class);
        opcoes = mock(OpcaoRepository.class);
        servico = new ProdutoValidacaoService(configs, grupos, opcoes);
        produto = new Produto();
        produto.setId(1L);
        produto.setTipo(TipoProduto.COMPOSTO);
        produto.setPrecoBase(new BigDecimal("35.00"));
    }

    private void cenario(EstrategiaPrecificacao e, GrupoOpcao g, Opcao... os) {
        ConfiguracaoPrecificacao c = new ConfiguracaoPrecificacao();
        c.setProdutoId(1L);
        c.setEstrategia(e);
        when(configs.findById(1L)).thenReturn(Optional.of(c));
        when(grupos.findByProdutoIdOrderByIdAsc(1L)).thenReturn(List.of(g));
        when(opcoes.findByGrupoIdOrderByIdAsc(g.getId())).thenReturn(List.of(os));
    }

    private GrupoOpcao grupo(int min, int max, ModalidadeCobranca m) {
        GrupoOpcao g = new GrupoOpcao();
        g.setId(10L);
        g.setNome("Sabores");
        g.setMinimo(min);
        g.setMaximo(max);
        g.setModalidadeCobranca(m);
        return g;
    }

    private Opcao opcao(int min, int max) {
        Opcao o = new Opcao();
        o.setId((long) (min * 31 + max));
        o.setNome("O" + min + max);
        o.setMinimo(min);
        o.setMaximo(max);
        return o;
    }

    @Test
    void configuracaoValidaNaoGeraProblemas() {
        cenario(EstrategiaPrecificacao.SOMA, grupo(1, 2, ModalidadeCobranca.PRINCIPAL), opcao(0, 1), opcao(0, 1));
        assertThat(servico.validar(produto)).isEmpty();
    }

    @Test
    void minimoDoGrupoMaiorQueSomaDosMaximosDasOpcoes() {
        cenario(EstrategiaPrecificacao.SOMA, grupo(3, 5, ModalidadeCobranca.PRINCIPAL), opcao(0, 1), opcao(0, 1));
        assertThat(servico.validar(produto)).anyMatch(p -> p.contains("mínimo do grupo"));
    }

    @Test
    void somaDosMinimosDasOpcoesMaiorQueMaximoDoGrupo() {
        cenario(EstrategiaPrecificacao.SOMA, grupo(1, 1, ModalidadeCobranca.PRINCIPAL), opcao(1, 1), opcao(1, 1));
        assertThat(servico.validar(produto)).anyMatch(p -> p.contains("soma dos mínimos"));
    }

    @Test
    void estrategiaNaoFixaExigeGrupoPrincipalObrigatorio() {
        cenario(EstrategiaPrecificacao.MEDIA, grupo(0, 2, ModalidadeCobranca.PRINCIPAL), opcao(0, 1));
        assertThat(servico.validar(produto)).anyMatch(p -> p.contains("mínimo >= 1"));
    }

    @Test
    void fixoNaoAceitaGrupoPrincipal() {
        cenario(EstrategiaPrecificacao.FIXO, grupo(1, 1, ModalidadeCobranca.PRINCIPAL), opcao(0, 1));
        assertThat(servico.validar(produto)).anyMatch(p -> p.contains("FIXO"));
    }

    @Test
    void fixoSemPrecoBaseEInvalido() {
        produto.setPrecoBase(null);
        cenario(EstrategiaPrecificacao.FIXO, grupo(0, 1, ModalidadeCobranca.GRATUITA), opcao(0, 1));
        assertThat(servico.validar(produto)).anyMatch(p -> p.contains("preço-base"));
    }
}
