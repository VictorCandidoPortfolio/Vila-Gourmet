package com.vilagourmet.dto;

import com.vilagourmet.model.enums.EstrategiaPrecificacao;
import com.vilagourmet.model.enums.ModalidadeCobranca;
import com.vilagourmet.model.enums.StatusProduto;
import com.vilagourmet.model.enums.TipoProduto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class CatalogoDTO {

    private CatalogoDTO() {
    }

    public record CategoriaRequest(@NotBlank @Size(max = 100) String nome, Boolean ativa) {
    }

    public record CategoriaResponse(Long id, String nome, boolean ativa) {
    }

    public record InsumoRequest(
            @NotBlank @Size(max = 150) String nome,
            String descricao,
            @Size(max = 500) String imagemUrl,
            @DecimalMin("0.00") BigDecimal valorReferencia,
            Boolean disponivel,
            String imagemBase64) {
    }

    public record InsumoResponse(Long id, String nome, String descricao, String imagemUrl,
                                 BigDecimal valorReferencia, boolean disponivel) {
    }

    /** {@code estrategia} e {@code participaPrecoBase} só se aplicam a produtos COMPOSTOS. */
    public record ProdutoRequest(
            @NotNull Long categoriaId,
            @NotNull TipoProduto tipo,
            @NotBlank @Size(max = 150) String nome,
            String descricao,
            @Size(max = 500) String imagemUrl,
            @DecimalMin("0.00") BigDecimal precoBase,
            Boolean disponivel,
            EstrategiaPrecificacao estrategia,
            Boolean participaPrecoBase,
            String imagemBase64) {
    }

    public record ProdutoResponse(
            Long id, Long categoriaId, String categoriaNome, TipoProduto tipo, String nome, String descricao,
            String imagemUrl, BigDecimal precoBase, boolean disponivel, StatusProduto status,
            EstrategiaPrecificacao estrategia, Boolean participaPrecoBase,
            List<GrupoResponse> grupos, List<AdicionalResponse> adicionais) {
    }

    public record GrupoRequest(
            @NotBlank @Size(max = 100) String nome,
            @NotNull @Min(0) Integer minimo,
            @NotNull @Min(0) Integer maximo,
            @NotNull ModalidadeCobranca modalidadeCobranca,
            Boolean ativo) {
    }

    public record GrupoResponse(Long id, String nome, int minimo, int maximo,
                                ModalidadeCobranca modalidadeCobranca, boolean ativo, List<OpcaoResponse> opcoes) {
    }

    /** A opção de um grupo é um insumo cadastrado: o nome vem do insumo. */
    public record OpcaoRequest(
            @NotNull Long insumoId,
            @NotNull @DecimalMin("0.00") BigDecimal preco,
            @NotNull @Min(0) Integer minimo,
            @NotNull @Min(0) Integer maximo,
            Boolean disponivel) {
    }

    public record OpcaoResponse(Long id, Long insumoId, String nome, BigDecimal preco,
                                int minimo, int maximo, boolean disponivel) {
    }

    public record AdicionalRequest(
            @NotBlank @Size(max = 150) String nome,
            @NotNull @DecimalMin("0.00") BigDecimal preco,
            Boolean disponivel) {
    }

    public record AdicionalResponse(Long id, String nome, BigDecimal preco, boolean disponivel) {
    }

    /** Foto em base64 (com ou sem o prefixo "data:image/...;base64,"). */
    public record ImagemRequest(@NotBlank String imagemBase64) {
    }

    public record ImagemResponse(String url, String publicId) {
    }

    public record DisponibilidadeRequest(@NotNull Boolean disponivel) {
    }

    public record ValidacaoProdutoResponse(boolean valido, List<String> problemas) {
    }
}
