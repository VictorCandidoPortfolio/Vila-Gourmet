package com.vilagourmet.service;

import com.vilagourmet.dto.CatalogoDTO.*;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.*;
import com.vilagourmet.model.enums.EstrategiaPrecificacao;
import com.vilagourmet.model.enums.StatusProduto;
import com.vilagourmet.model.enums.TipoProduto;
import com.vilagourmet.repository.*;
import com.vilagourmet.service.PrecificacaoService.CatalogoProduto;
import com.vilagourmet.service.PrecificacaoService.GrupoCatalogo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo: administração de categorias, insumos, produtos, grupos, opções e adicionais, e consulta pública. */
@Service
public class CatalogoService {

    private final CategoriaRepository categorias;
    private final InsumoRepository insumos;
    private final ProdutoRepository produtos;
    private final ConfiguracaoPrecificacaoRepository configs;
    private final GrupoOpcaoRepository grupos;
    private final OpcaoRepository opcoes;
    private final AdicionalSimplesRepository adicionais;
    private final ProdutoValidacaoService validacao;
    private final PizzaSalvaRepository pizzasSalvas;
    private final ImagemService imagens;

    public CatalogoService(CategoriaRepository categorias, InsumoRepository insumos, ProdutoRepository produtos,
                           ConfiguracaoPrecificacaoRepository configs, GrupoOpcaoRepository grupos,
                           OpcaoRepository opcoes, AdicionalSimplesRepository adicionais,
                           ProdutoValidacaoService validacao, PizzaSalvaRepository pizzasSalvas,
                           ImagemService imagens) {
        this.categorias = categorias;
        this.insumos = insumos;
        this.produtos = produtos;
        this.configs = configs;
        this.grupos = grupos;
        this.opcoes = opcoes;
        this.adicionais = adicionais;
        this.validacao = validacao;
        this.pizzasSalvas = pizzasSalvas;
        this.imagens = imagens;
    }

    // ---------------------------------------------------------------- categorias

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarCategorias() {
        return categorias.findAllByOrderByNomeAsc().stream().map(this::paraResponse).toList();
    }

    @Transactional
    public CategoriaResponse criarCategoria(CategoriaRequest req) {
        if (categorias.existsByNomeIgnoreCase(req.nome().trim())) {
            throw new NegocioException("Já existe uma categoria com esse nome");
        }
        Categoria c = new Categoria();
        c.setNome(req.nome().trim());
        c.setAtiva(req.ativa() == null || req.ativa());
        return paraResponse(categorias.save(c));
    }

    @Transactional
    public CategoriaResponse atualizarCategoria(Long id, CategoriaRequest req) {
        Categoria c = categorias.findById(id).orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada"));
        if (!c.getNome().equalsIgnoreCase(req.nome().trim()) && categorias.existsByNomeIgnoreCase(req.nome().trim())) {
            throw new NegocioException("Já existe uma categoria com esse nome");
        }
        c.setNome(req.nome().trim());
        if (req.ativa() != null) {
            c.setAtiva(req.ativa());
        }
        return paraResponse(c);
    }

    /**
     * Exclui a categoria somente se nenhum produto a usar (os produtos dependem dela). Caso contrário,
     * explica quais produtos usam e sugere mover os produtos ou desativar a categoria.
     */
    @Transactional
    public void excluirCategoria(Long id) {
        Categoria c = categorias.findById(id).orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada"));
        List<Produto> usando = produtos.findByCategoriaIdOrderByNomeAsc(id);
        if (!usando.isEmpty()) {
            throw new NegocioException("A categoria “" + c.getNome() + "” não pode ser excluída porque está em uso por "
                    + usando.size() + " produto(s). Mova os produtos para outra categoria ou desative a categoria.",
                    usando.stream().map(Produto::getNome).limit(15).toList());
        }
        categorias.delete(c);
        categorias.flush();
    }

    // ---------------------------------------------------------------- insumos

    @Transactional(readOnly = true)
    public List<InsumoResponse> listarInsumos() {
        return insumos.findAllByOrderByNomeAsc().stream().map(this::paraResponse).toList();
    }

    @Transactional
    public InsumoResponse criarInsumo(InsumoRequest req) {
        return paraResponse(insumos.save(preencher(new Insumo(), req)));
    }

    @Transactional
    public InsumoResponse atualizarInsumo(Long id, InsumoRequest req) {
        Insumo i = insumos.findById(id).orElseThrow(() -> new NaoEncontradoException("Insumo não encontrado"));
        preencher(i, req);
        sincronizarOpcoes(i);
        return paraResponse(i);
    }

    /**
     * As opções são insumos: renomear o insumo renomeia suas opções, e um insumo indisponível torna
     * indisponíveis as opções que o usam (produtos publicados que ficarem inválidos voltam a rascunho).
     * Reativar o insumo não reativa as opções automaticamente.
     */
    private void sincronizarOpcoes(Insumo insumo) {
        java.util.Set<Produto> afetados = new java.util.LinkedHashSet<>();
        for (Opcao o : opcoes.findByInsumoId(insumo.getId())) {
            o.setNome(insumo.getNome());
            if (!insumo.isDisponivel()) {
                o.setDisponivel(false);
            }
            afetados.add(o.getGrupo().getProduto());
        }
        afetados.forEach(this::revalidarPublicacao);
    }

    /**
     * Exclui o insumo somente se nenhuma opção de produto o usar. Caso contrário, lista os produtos
     * afetados e sugere remover o insumo das opções ou apenas desativá-lo. A foto enviada ao Cloudinary
     * não é apagada de lá.
     */
    @Transactional
    public void excluirInsumo(Long id) {
        Insumo i = insumos.findById(id).orElseThrow(() -> new NaoEncontradoException("Insumo não encontrado"));
        List<String> produtosAfetados = opcoes.findByInsumoId(id).stream()
                .map(o -> o.getGrupo().getProduto().getNome()).distinct().sorted().toList();
        if (!produtosAfetados.isEmpty()) {
            throw new NegocioException("O insumo “" + i.getNome() + "” não pode ser excluído porque é opção em "
                    + produtosAfetados.size() + " produto(s). Retire-o dos grupos desses produtos ou apenas desative o insumo.",
                    produtosAfetados.stream().limit(15).toList());
        }
        insumos.delete(i);
        insumos.flush();
    }

    private Insumo preencher(Insumo i, InsumoRequest req) {
        i.setNome(req.nome().trim());
        i.setDescricao(req.descricao());
        i.setValorReferencia(req.valorReferencia());
        if (req.disponivel() != null) {
            i.setDisponivel(req.disponivel());
        }
        i.setImagemUrl(resolverImagem(req.imagemBase64(), req.imagemUrl(), "insumos"));
        return i;
    }

    // ---------------------------------------------------------------- produtos

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarProdutos() {
        return produtos.findAllByOrderByNomeAsc().stream().map(p -> montar(p, false)).toList();
    }

    @Transactional(readOnly = true)
    public ProdutoResponse obterProduto(Long id) {
        return montar(exigirProduto(id), false);
    }

    @Transactional
    public ProdutoResponse criarProduto(ProdutoRequest req) {
        Produto p = new Produto();
        p.setTipo(req.tipo());
        preencher(p, req);
        produtos.save(p);
        salvarConfiguracao(p, req);
        return montar(p, false);
    }

    @Transactional
    public ProdutoResponse atualizarProduto(Long id, ProdutoRequest req) {
        Produto p = exigirProduto(id);
        if (p.getTipo() != req.tipo()) {
            throw new NegocioException("O tipo do produto não pode ser alterado; crie um novo produto");
        }
        preencher(p, req);
        salvarConfiguracao(p, req);
        revalidarPublicacao(p);
        return montar(p, false);
    }

    private void preencher(Produto p, ProdutoRequest req) {
        p.setCategoria(categorias.findById(req.categoriaId()).orElseThrow(() -> new NaoEncontradoException("Categoria não encontrada")));
        p.setNome(req.nome().trim());
        p.setDescricao(req.descricao());
        p.setPrecoBase(req.precoBase());
        if (req.disponivel() != null) {
            p.setDisponivel(req.disponivel());
        }
        if (p.getTipo() == TipoProduto.SIMPLES && req.precoBase() == null) {
            throw new NegocioException("Produto simples exige preço de venda");
        }
        // por último: só envia a imagem ao Cloudinary depois de validar o resto
        p.setImagemUrl(resolverImagem(req.imagemBase64(), req.imagemUrl(), "produtos"));
        p.setAtualizadoEm(LocalDateTime.now());
    }

    /** Foto nova (base64) é enviada ao Cloudinary e vira URL; sem foto nova, mantém a URL informada (ou remove, se vazia). */
    private String resolverImagem(String base64, String urlAtual, String pasta) {
        if (base64 != null && !base64.isBlank()) {
            return imagens.enviar(base64, pasta).url();
        }
        return urlAtual == null || urlAtual.isBlank() ? null : urlAtual.trim();
    }

    private void salvarConfiguracao(Produto p, ProdutoRequest req) {
        if (p.getTipo() == TipoProduto.SIMPLES) {
            if (req.estrategia() != null) {
                throw new NegocioException("Produto simples não usa estratégia de precificação");
            }
            return;
        }
        if (req.estrategia() == null) {
            throw new NegocioException("Produto composto exige a estratégia de precificação");
        }
        ConfiguracaoPrecificacao cfg = configs.findById(p.getId()).orElseGet(() -> {
            ConfiguracaoPrecificacao nova = new ConfiguracaoPrecificacao();
            nova.setProdutoId(p.getId());
            return nova;
        });
        cfg.setEstrategia(req.estrategia());
        cfg.setParticipaPrecoBase(req.estrategia() != EstrategiaPrecificacao.FIXO && Boolean.TRUE.equals(req.participaPrecoBase()));
        configs.save(cfg);
    }

    @Transactional(readOnly = true)
    public ValidacaoProdutoResponse validarProduto(Long id) {
        List<String> problemas = validacao.validar(exigirProduto(id));
        return new ValidacaoProdutoResponse(problemas.isEmpty(), problemas);
    }

    /**
     * Desativar tira o produto do catálogo sem apagá-lo (e o reativa depois); a publicação
     * (rascunho/publicado) não muda.
     */
    @Transactional
    public ProdutoResponse alterarDisponibilidade(Long id, boolean disponivel) {
        Produto p = exigirProduto(id);
        p.setDisponivel(disponivel);
        p.setAtualizadoEm(LocalDateTime.now());
        return montar(p, false);
    }

    /**
     * Exclusão definitiva do produto com seus grupos, opções, adicionais e configuração de preço.
     * Pedidos já feitos não são afetados: itens, escolhas e adicionais guardam o snapshot (nomes e preços)
     * e apenas perdem a referência ao cadastro. Pizzas salvas de clientes com este produto são removidas.
     * Para só tirar o produto de venda, use a desativação.
     */
    @Transactional
    public void excluirProduto(Long id) {
        Produto p = exigirProduto(id);
        List<GrupoOpcao> gs = grupos.findByProdutoIdOrderByIdAsc(id);
        for (GrupoOpcao g : gs) {
            opcoes.deleteAll(opcoes.findByGrupoIdOrderByIdAsc(g.getId()));
        }
        opcoes.flush();
        grupos.deleteAll(gs);
        grupos.flush();
        adicionais.deleteAll(adicionais.findByProdutoIdOrderByIdAsc(id));
        adicionais.flush();
        configs.findById(id).ifPresent(configs::delete);
        pizzasSalvas.deleteByProdutoId(id);
        pizzasSalvas.flush();
        produtos.delete(p);
        produtos.flush();
    }

    /** Publica o produto somente se a configuração for válida; caso contrário explica os conflitos. */
    @Transactional
    public ProdutoResponse publicar(Long id) {
        Produto p = exigirProduto(id);
        List<String> problemas = validacao.validar(p);
        if (!problemas.isEmpty()) {
            throw new NegocioException("Configuração inválida; o produto não pode ser publicado", problemas);
        }
        p.setStatus(StatusProduto.PUBLICADO);
        p.setAtualizadoEm(LocalDateTime.now());
        return montar(p, false);
    }

    @Transactional
    public ProdutoResponse despublicar(Long id) {
        Produto p = exigirProduto(id);
        p.setStatus(StatusProduto.RASCUNHO);
        p.setAtualizadoEm(LocalDateTime.now());
        return montar(p, false);
    }

    // ---------------------------------------------------------------- grupos, opções e adicionais

    @Transactional
    public ProdutoResponse criarGrupo(Long produtoId, GrupoRequest req) {
        Produto p = exigirComposto(produtoId);
        GrupoOpcao g = new GrupoOpcao();
        g.setProduto(p);
        preencher(g, req);
        grupos.save(g);
        revalidarPublicacao(p);
        return montar(p, false);
    }

    @Transactional
    public ProdutoResponse atualizarGrupo(Long grupoId, GrupoRequest req) {
        GrupoOpcao g = grupos.findById(grupoId).orElseThrow(() -> new NaoEncontradoException("Grupo não encontrado"));
        preencher(g, req);
        revalidarPublicacao(g.getProduto());
        return montar(g.getProduto(), false);
    }

    private void preencher(GrupoOpcao g, GrupoRequest req) {
        if (req.maximo() < req.minimo()) {
            throw new NegocioException("O máximo do grupo não pode ser menor que o mínimo");
        }
        g.setNome(req.nome().trim());
        g.setMinimo(req.minimo());
        g.setMaximo(req.maximo());
        g.setModalidadeCobranca(req.modalidadeCobranca());
        if (req.ativo() != null) {
            g.setAtivo(req.ativo());
        }
    }

    @Transactional
    public ProdutoResponse criarOpcao(Long grupoId, OpcaoRequest req) {
        GrupoOpcao g = grupos.findById(grupoId).orElseThrow(() -> new NaoEncontradoException("Grupo não encontrado"));
        Opcao o = new Opcao();
        o.setGrupo(g);
        preencher(o, req);
        opcoes.save(o);
        revalidarPublicacao(g.getProduto());
        return montar(g.getProduto(), false);
    }

    @Transactional
    public ProdutoResponse atualizarOpcao(Long opcaoId, OpcaoRequest req) {
        Opcao o = opcoes.findById(opcaoId).orElseThrow(() -> new NaoEncontradoException("Opção não encontrada"));
        preencher(o, req);
        revalidarPublicacao(o.getGrupo().getProduto());
        return montar(o.getGrupo().getProduto(), false);
    }

    private void preencher(Opcao o, OpcaoRequest req) {
        if (req.maximo() < req.minimo()) {
            throw new NegocioException("O máximo da opção não pode ser menor que o mínimo");
        }
        Insumo insumo = insumos.findById(req.insumoId()).orElseThrow(() -> new NaoEncontradoException("Insumo não encontrado"));
        boolean repetido = opcoes.findByGrupoIdOrderByIdAsc(o.getGrupo().getId()).stream()
                .anyMatch(x -> x.getInsumoId().equals(insumo.getId()) && !x.getId().equals(o.getId()));
        if (repetido) {
            throw new NegocioException("O insumo '" + insumo.getNome() + "' já é uma opção deste grupo");
        }
        if (!insumo.isDisponivel() && (req.disponivel() == null || req.disponivel())) {
            throw new NegocioException("O insumo '" + insumo.getNome() + "' está indisponível");
        }
        o.setInsumoId(insumo.getId());
        o.setNome(insumo.getNome());
        o.setPreco(req.preco());
        o.setMinimo(req.minimo());
        o.setMaximo(req.maximo());
        if (req.disponivel() != null) {
            o.setDisponivel(req.disponivel());
        }
    }

    @Transactional
    public ProdutoResponse criarAdicional(Long produtoId, AdicionalRequest req) {
        Produto p = exigirProduto(produtoId);
        if (p.getTipo() != TipoProduto.SIMPLES) {
            throw new NegocioException("Adicionais simples só podem ser cadastrados em produtos simples");
        }
        AdicionalSimples a = new AdicionalSimples();
        a.setProduto(p);
        preencher(a, req);
        adicionais.save(a);
        return montar(p, false);
    }

    @Transactional
    public ProdutoResponse atualizarAdicional(Long adicionalId, AdicionalRequest req) {
        AdicionalSimples a = adicionais.findById(adicionalId).orElseThrow(() -> new NaoEncontradoException("Adicional não encontrado"));
        preencher(a, req);
        return montar(a.getProduto(), false);
    }

    private void preencher(AdicionalSimples a, AdicionalRequest req) {
        a.setNome(req.nome().trim());
        a.setPreco(req.preco());
        if (req.disponivel() != null) {
            a.setDisponivel(req.disponivel());
        }
    }

    /** Um produto publicado que deixa de ser válido após uma edição volta a rascunho. */
    private void revalidarPublicacao(Produto p) {
        if (p.getStatus() == StatusProduto.PUBLICADO && !validacao.validar(p).isEmpty()) {
            p.setStatus(StatusProduto.RASCUNHO);
        }
    }

    // ---------------------------------------------------------------- consulta pública e carga para precificação

    @Transactional(readOnly = true)
    public List<ProdutoResponse> catalogoPublico() {
        return produtos.findByStatusAndDisponivelTrueAndCategoriaAtivaTrueOrderByNomeAsc(StatusProduto.PUBLICADO)
                .stream().map(p -> montar(p, true)).toList();
    }

    @Transactional(readOnly = true)
    public CatalogoProduto carregarCatalogo(Long produtoId) {
        Produto p = produtos.findById(produtoId).orElseThrow(() -> new NaoEncontradoException("Produto não encontrado"));
        ConfiguracaoPrecificacao cfg = configs.findById(produtoId).orElse(null);
        List<GrupoOpcao> gs = grupos.findByProdutoIdOrderByIdAsc(produtoId);
        Map<Long, List<Opcao>> porGrupo = gs.isEmpty() ? Map.of()
                : opcoes.findByGrupoIdInOrderByIdAsc(gs.stream().map(GrupoOpcao::getId).toList()).stream()
                        .collect(Collectors.groupingBy(o -> o.getGrupo().getId()));
        List<GrupoCatalogo> catalogo = gs.stream().map(g -> new GrupoCatalogo(g, porGrupo.getOrDefault(g.getId(), List.of()))).toList();
        return new CatalogoProduto(p, cfg, catalogo, adicionais.findByProdutoIdOrderByIdAsc(produtoId));
    }

    // ---------------------------------------------------------------- montagem de respostas

    private ProdutoResponse montar(Produto p, boolean somenteAtivos) {
        ConfiguracaoPrecificacao cfg = configs.findById(p.getId()).orElse(null);
        List<GrupoOpcao> gs = grupos.findByProdutoIdOrderByIdAsc(p.getId()).stream()
                .filter(g -> !somenteAtivos || g.isAtivo()).toList();
        Map<Long, List<Opcao>> porGrupo = gs.isEmpty() ? Map.of()
                : opcoes.findByGrupoIdInOrderByIdAsc(gs.stream().map(GrupoOpcao::getId).toList()).stream()
                        .filter(o -> !somenteAtivos || o.isDisponivel())
                        .collect(Collectors.groupingBy(o -> o.getGrupo().getId()));
        List<GrupoResponse> grupoResponses = gs.stream().map(g -> new GrupoResponse(g.getId(), g.getNome(), g.getMinimo(),
                g.getMaximo(), g.getModalidadeCobranca(), g.isAtivo(),
                porGrupo.getOrDefault(g.getId(), List.of()).stream().map(this::paraResponse).toList())).toList();
        List<AdicionalResponse> adicionalResponses = adicionais.findByProdutoIdOrderByIdAsc(p.getId()).stream()
                .filter(a -> !somenteAtivos || a.isDisponivel())
                .map(a -> new AdicionalResponse(a.getId(), a.getNome(), a.getPreco(), a.isDisponivel())).toList();
        return new ProdutoResponse(p.getId(), p.getCategoria().getId(), p.getCategoria().getNome(), p.getTipo(), p.getNome(),
                p.getDescricao(), p.getImagemUrl(), p.getPrecoBase(), p.isDisponivel(), p.getStatus(),
                cfg == null ? null : cfg.getEstrategia(), cfg == null ? null : cfg.isParticipaPrecoBase(),
                grupoResponses, adicionalResponses);
    }

    private OpcaoResponse paraResponse(Opcao o) {
        return new OpcaoResponse(o.getId(), o.getInsumoId(), o.getNome(), o.getPreco(), o.getMinimo(), o.getMaximo(), o.isDisponivel());
    }

    private CategoriaResponse paraResponse(Categoria c) {
        return new CategoriaResponse(c.getId(), c.getNome(), c.isAtiva());
    }

    private InsumoResponse paraResponse(Insumo i) {
        return new InsumoResponse(i.getId(), i.getNome(), i.getDescricao(), i.getImagemUrl(), i.getValorReferencia(), i.isDisponivel());
    }

    private Produto exigirProduto(Long id) {
        return produtos.findById(id).orElseThrow(() -> new NaoEncontradoException("Produto não encontrado"));
    }

    private Produto exigirComposto(Long id) {
        Produto p = exigirProduto(id);
        if (p.getTipo() != TipoProduto.COMPOSTO) {
            throw new NegocioException("Grupos de opções só podem ser cadastrados em produtos compostos");
        }
        return p;
    }
}
