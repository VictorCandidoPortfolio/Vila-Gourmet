package com.vilagourmet.dto;

import com.vilagourmet.model.enums.EstrategiaPrecificacao;
import com.vilagourmet.model.enums.ModalidadeCobranca;
import com.vilagourmet.model.enums.TipoProduto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CotacaoDTO {

    private CotacaoDTO() {
    }

    public record SelecaoOpcao(@NotNull Long opcaoId, @Min(1) int quantidade) {
    }

    public record SelecaoAdicional(@NotNull Long adicionalId, @Min(1) int quantidade) {
    }

    /** Montagem de um item: produto, quantidade e seleções. */
    public record CotacaoRequest(
            @NotNull Long produtoId,
            @NotNull @Min(1) Integer quantidade,
            @Valid List<SelecaoOpcao> escolhas,
            @Valid List<SelecaoAdicional> adicionais) {
    }

    public record LinhaEscolha(Long grupoId, Long opcaoId, String nomeGrupo, String nomeOpcao,
                               ModalidadeCobranca modalidade, int quantidade,
                               BigDecimal precoUnitario, BigDecimal subtotal) {
    }

    public record LinhaAdicional(Long adicionalId, String nome, int quantidade,
                                 BigDecimal precoUnitario, BigDecimal subtotal) {
    }

    /** Resultado calculado no servidor; também é o snapshot gravado no pedido. */
    public record ItemCotado(Long produtoId, String nomeProduto, TipoProduto tipo, BigDecimal precoBase,
                             EstrategiaPrecificacao estrategia, Boolean participaPrecoBase, int quantidade,
                             BigDecimal precoUnitario, BigDecimal subtotal,
                             List<LinhaEscolha> escolhas, List<LinhaAdicional> adicionais) {
    }

    /** O token assinado garante o preço durante a sessão de montagem até {@code expiraEm}. */
    public record CotacaoResponse(ItemCotado item, String cotacaoToken, Instant expiraEm) {
    }
}
