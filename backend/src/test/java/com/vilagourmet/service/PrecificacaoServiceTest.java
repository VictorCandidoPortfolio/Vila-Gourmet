package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vilagourmet.dto.CotacaoDTO.ItemCotado;
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
import com.vilagourmet.service.PrecificacaoService.CatalogoProduto;
import com.vilagourmet.service.PrecificacaoService.GrupoCatalogo;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Casos de aceitação do motor de precificação (exemplo de referência da documentação v1.2). */
class PrecificacaoServiceTest {

    private final PrecificacaoService servico = new PrecificacaoService();

    private static Produto produto(TipoProduto tipo, String precoBase) {
        Produto p = new Produto();
        p.setId(1L);
        p.setNome("Pizza");
        p.setTipo(tipo);
        p.setStatus(StatusProduto.PUBLICADO);
        p.setPrecoBase(precoBase == null ? null : new BigDecimal(precoBase));
        return p;
    }

    private static ConfiguracaoPrecificacao config(EstrategiaPrecificacao e, boolean incluirBase) {
        ConfiguracaoPrecificacao c = new ConfiguracaoPrecificacao();
        c.setProdutoId(1L);
        c.setEstrategia(e);
        c.setParticipaPrecoBase(incluirBase);
        return c;
    }

    private static GrupoOpcao grupo(long id, int min, int max, ModalidadeCobranca m) {
        GrupoOpcao g = new GrupoOpcao();
        g.setId(id);
        g.setNome("Grupo " + id);
        g.setMinimo(min);
        g.setMaximo(max);
        g.setModalidadeCobranca(m);
        return g;
    }

    private static Opcao opcao(long id, GrupoOpcao g, String preco, int min, int max) {
        Opcao o = new Opcao();
        o.setId(id);
        o.setGrupo(g);
        o.setNome("Opção " + id);
        o.setPreco(new BigDecimal(preco));
        o.setMinimo(min);
        o.setMaximo(max);
        return o;
    }

    /** Preço-base 35; sabores R$ 20 e R$ 24 (principal); adicional separado R$ 6. */
    private CatalogoProduto exemplo(EstrategiaPrecificacao e, boolean incluirBase) {
        GrupoOpcao sabores = grupo(10, 1, 2, ModalidadeCobranca.PRINCIPAL);
        GrupoOpcao extras = grupo(11, 0, 1, ModalidadeCobranca.SEPARADA);
        return new CatalogoProduto(produto(TipoProduto.COMPOSTO, "35.00"), config(e, incluirBase),
                List.of(new GrupoCatalogo(sabores, List.of(opcao(1, sabores, "20.00", 0, 1), opcao(2, sabores, "24.00", 0, 1))),
                        new GrupoCatalogo(extras, List.of(opcao(3, extras, "6.00", 0, 1)))),
                List.of());
    }

    private BigDecimal unitario(EstrategiaPrecificacao e, boolean incluirBase) {
        ItemCotado r = servico.calcular(exemplo(e, incluirBase), 1,
                List.of(new SelecaoOpcao(1L, 1), new SelecaoOpcao(2L, 1), new SelecaoOpcao(3L, 1)), List.of());
        return r.precoUnitario();
    }

    @Test
    void exemploDeReferencia() {
        assertThat(unitario(EstrategiaPrecificacao.SOMA, false)).isEqualByComparingTo("50.00");        // 44 + 6
        assertThat(unitario(EstrategiaPrecificacao.SOMA, true)).isEqualByComparingTo("85.00");         // 79 + 6
        assertThat(unitario(EstrategiaPrecificacao.MEDIA, false)).isEqualByComparingTo("28.00");       // 22 + 6
        assertThat(unitario(EstrategiaPrecificacao.MEDIA, true)).isEqualByComparingTo("32.33");        // 26,33 + 6
        assertThat(unitario(EstrategiaPrecificacao.MAIOR_VALOR, false)).isEqualByComparingTo("30.00"); // 24 + 6
        assertThat(unitario(EstrategiaPrecificacao.MAIOR_VALOR, true)).isEqualByComparingTo("41.00");  // 35 + 6
        assertThat(unitario(EstrategiaPrecificacao.FIXO, false)).isEqualByComparingTo("41.00");        // 35 + 6
    }

    @Test
    void quantidadeMultiplicaSubtotal() {
        ItemCotado r = servico.calcular(exemplo(EstrategiaPrecificacao.SOMA, false), 3,
                List.of(new SelecaoOpcao(1L, 1), new SelecaoOpcao(2L, 1)), List.of());
        assertThat(r.precoUnitario()).isEqualByComparingTo("44.00");
        assertThat(r.subtotal()).isEqualByComparingTo("132.00");
    }

    @Test
    void selecaoRepetidaPermitidaDentroDoLimite() {
        GrupoOpcao g = grupo(10, 1, 3, ModalidadeCobranca.PRINCIPAL);
        CatalogoProduto cat = new CatalogoProduto(produto(TipoProduto.COMPOSTO, null), config(EstrategiaPrecificacao.SOMA, false),
                List.of(new GrupoCatalogo(g, List.of(opcao(1, g, "10.00", 0, 3)))), List.of());
        assertThat(servico.calcular(cat, 1, List.of(new SelecaoOpcao(1L, 3)), List.of()).precoUnitario()).isEqualByComparingTo("30.00");
        assertThatThrownBy(() -> servico.calcular(cat, 1, List.of(new SelecaoOpcao(1L, 4)), List.of()))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void arredondaMeiaParaCima() {
        GrupoOpcao g = grupo(10, 2, 2, ModalidadeCobranca.PRINCIPAL);
        CatalogoProduto cat = new CatalogoProduto(produto(TipoProduto.COMPOSTO, null), config(EstrategiaPrecificacao.MEDIA, false),
                List.of(new GrupoCatalogo(g, List.of(opcao(1, g, "0.01", 0, 1), opcao(2, g, "0.02", 0, 1)))), List.of());
        // (0,01 + 0,02) / 2 = 0,015 -> 0,02
        assertThat(servico.calcular(cat, 1, List.of(new SelecaoOpcao(1L, 1), new SelecaoOpcao(2L, 1)), List.of()).precoUnitario())
                .isEqualByComparingTo("0.02");
    }

    @Test
    void opcaoDeValorZeroParticipaDaMedia() {
        GrupoOpcao g = grupo(10, 2, 2, ModalidadeCobranca.PRINCIPAL);
        CatalogoProduto cat = new CatalogoProduto(produto(TipoProduto.COMPOSTO, null), config(EstrategiaPrecificacao.MEDIA, false),
                List.of(new GrupoCatalogo(g, List.of(opcao(1, g, "0.00", 0, 1), opcao(2, g, "30.00", 0, 1)))), List.of());
        assertThat(servico.calcular(cat, 1, List.of(new SelecaoOpcao(1L, 1), new SelecaoOpcao(2L, 1)), List.of()).precoUnitario())
                .isEqualByComparingTo("15.00");
    }

    @Test
    void grupoObrigatorioNaoPodeFicarVazio() {
        assertThatThrownBy(() -> servico.calcular(exemplo(EstrategiaPrecificacao.SOMA, false), 1,
                List.of(new SelecaoOpcao(3L, 1)), List.of()))
                .isInstanceOf(NegocioException.class).hasMessageContaining("exige ao menos");
    }

    @Test
    void respeitaMaximoDoGrupo() {
        GrupoOpcao g = grupo(10, 1, 1, ModalidadeCobranca.PRINCIPAL);
        CatalogoProduto cat = new CatalogoProduto(produto(TipoProduto.COMPOSTO, null), config(EstrategiaPrecificacao.SOMA, false),
                List.of(new GrupoCatalogo(g, List.of(opcao(1, g, "10.00", 0, 1), opcao(2, g, "12.00", 0, 1)))), List.of());
        assertThatThrownBy(() -> servico.calcular(cat, 1, List.of(new SelecaoOpcao(1L, 1), new SelecaoOpcao(2L, 1)), List.of()))
                .isInstanceOf(NegocioException.class).hasMessageContaining("aceita entre");
    }

    @Test
    void minimoDaOpcaoValeQuandoOGrupoEUsado() {
        GrupoOpcao g = grupo(10, 0, 3, ModalidadeCobranca.GRATUITA);
        GrupoOpcao base = grupo(11, 1, 1, ModalidadeCobranca.PRINCIPAL);
        CatalogoProduto cat = new CatalogoProduto(produto(TipoProduto.COMPOSTO, null), config(EstrategiaPrecificacao.SOMA, false),
                List.of(new GrupoCatalogo(base, List.of(opcao(1, base, "10.00", 0, 1))),
                        new GrupoCatalogo(g, List.of(opcao(2, g, "0.00", 1, 2), opcao(3, g, "0.00", 0, 1)))), List.of());
        // grupo gratuito não usado: mínimo da opção 2 não se aplica
        assertThat(servico.calcular(cat, 1, List.of(new SelecaoOpcao(1L, 1)), List.of()).precoUnitario()).isEqualByComparingTo("10.00");
        // grupo usado só com a opção 3: o mínimo da opção 2 passa a valer
        assertThatThrownBy(() -> servico.calcular(cat, 1, List.of(new SelecaoOpcao(1L, 1), new SelecaoOpcao(3L, 1)), List.of()))
                .isInstanceOf(NegocioException.class).hasMessageContaining("exige ao menos");
    }

    @Test
    void opcaoIndisponivelNaoPodeSerSelecionada() {
        CatalogoProduto cat = exemplo(EstrategiaPrecificacao.SOMA, false);
        cat.grupos().get(0).opcoes().get(0).setDisponivel(false);
        assertThatThrownBy(() -> servico.calcular(cat, 1, List.of(new SelecaoOpcao(1L, 1)), List.of()))
                .isInstanceOf(NegocioException.class).hasMessageContaining("indispon");
    }

    @Test
    void produtoNaoPublicadoNaoPodeSerCotado() {
        CatalogoProduto cat = exemplo(EstrategiaPrecificacao.SOMA, false);
        cat.produto().setStatus(StatusProduto.RASCUNHO);
        assertThatThrownBy(() -> servico.calcular(cat, 1, List.of(new SelecaoOpcao(1L, 1)), List.of()))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void produtoSimplesSomaAdicionais() {
        AdicionalSimples borda = new AdicionalSimples();
        borda.setId(7L);
        borda.setNome("Borda");
        borda.setPreco(new BigDecimal("5.50"));
        CatalogoProduto cat = new CatalogoProduto(produto(TipoProduto.SIMPLES, "30.00"), null, List.of(), List.of(borda));
        ItemCotado r = servico.calcular(cat, 2, List.of(), List.of(new SelecaoAdicional(7L, 2)));
        assertThat(r.precoUnitario()).isEqualByComparingTo("41.00");
        assertThat(r.subtotal()).isEqualByComparingTo("82.00");
        assertThatThrownBy(() -> servico.calcular(cat, 1, List.of(), List.of(new SelecaoAdicional(99L, 1))))
                .isInstanceOf(NegocioException.class);
    }
}
