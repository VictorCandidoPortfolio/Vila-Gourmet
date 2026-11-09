package com.vilagourmet.service;

import com.vilagourmet.dto.CotacaoDTO.CotacaoRequest;
import com.vilagourmet.dto.CotacaoDTO.CotacaoResponse;
import com.vilagourmet.dto.CotacaoDTO.ItemCotado;
import com.vilagourmet.dto.CotacaoDTO.LinhaAdicional;
import com.vilagourmet.dto.CotacaoDTO.LinhaEscolha;
import com.vilagourmet.dto.PedidoDTO.ItemRequest;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.AdicionalSimples;
import com.vilagourmet.model.Opcao;
import com.vilagourmet.model.Produto;
import com.vilagourmet.model.enums.StatusProduto;
import com.vilagourmet.repository.AdicionalSimplesRepository;
import com.vilagourmet.repository.OpcaoRepository;
import com.vilagourmet.repository.ProdutoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cotação de preço. Durante a sessão de montagem o preço fica garantido por um token assinado
 * (nada é persistido): alterações posteriores de preço no catálogo não alteram a cotação emitida.
 * Na confirmação, a disponibilidade dos itens é revalidada; só o preço é honrado.
 */
@Service
public class CotacaoService {

    private final CatalogoService catalogo;
    private final PrecificacaoService precificacao;
    private final JwtService jwt;
    private final ProdutoRepository produtos;
    private final OpcaoRepository opcoes;
    private final AdicionalSimplesRepository adicionais;

    public CotacaoService(CatalogoService catalogo, PrecificacaoService precificacao, JwtService jwt,
                          ProdutoRepository produtos, OpcaoRepository opcoes, AdicionalSimplesRepository adicionais) {
        this.catalogo = catalogo;
        this.precificacao = precificacao;
        this.jwt = jwt;
        this.produtos = produtos;
        this.opcoes = opcoes;
        this.adicionais = adicionais;
    }

    @Transactional(readOnly = true)
    public CotacaoResponse cotar(CotacaoRequest req) {
        ItemCotado item = precificacao.calcular(catalogo.carregarCatalogo(req.produtoId()), req.quantidade(),
                req.escolhas(), req.adicionais());
        JwtService.CotacaoEmitida emitida = jwt.emitirCotacao(item);
        return new CotacaoResponse(item, emitida.token(), emitida.expiraEm());
    }

    /** Resolve o item de um pedido: honra a cotação (se houver token) ou calcula agora. */
    @Transactional(readOnly = true)
    public ItemCotado resolver(ItemRequest item) {
        if (item.cotacaoToken() != null && !item.cotacaoToken().isBlank()) {
            ItemCotado cotado = jwt.lerCotacao(item.cotacaoToken())
                    .orElseThrow(() -> new NegocioException(HttpStatus.CONFLICT,
                            "A cotação expirou ou é inválida. Refaça a montagem para obter o preço vigente."));
            revalidarDisponibilidade(cotado);
            return cotado;
        }
        if (item.produtoId() == null || item.quantidade() == null) {
            throw new NegocioException("Informe o produto e a quantidade, ou uma cotação");
        }
        return precificacao.calcular(catalogo.carregarCatalogo(item.produtoId()), item.quantidade(),
                item.escolhas(), item.adicionais());
    }

    private void revalidarDisponibilidade(ItemCotado c) {
        Produto p = produtos.findById(c.produtoId())
                .orElseThrow(() -> new NegocioException(HttpStatus.CONFLICT, "O produto da cotação não existe mais"));
        if (p.getStatus() != StatusProduto.PUBLICADO || !p.isDisponivel()) {
            throw new NegocioException(HttpStatus.CONFLICT, "O produto '" + p.getNome() + "' não está mais disponível");
        }
        for (LinhaEscolha l : c.escolhas()) {
            Opcao o = opcoes.findById(l.opcaoId()).orElse(null);
            if (o == null || !o.isDisponivel() || !o.getGrupo().isAtivo()) {
                throw new NegocioException(HttpStatus.CONFLICT,
                        "A opção '" + l.nomeOpcao() + "' não está mais disponível; refaça a montagem");
            }
        }
        for (LinhaAdicional l : c.adicionais()) {
            AdicionalSimples a = adicionais.findById(l.adicionalId()).orElse(null);
            if (a == null || !a.isDisponivel()) {
                throw new NegocioException(HttpStatus.CONFLICT,
                        "O adicional '" + l.nome() + "' não está mais disponível; refaça a montagem");
            }
        }
    }
}
