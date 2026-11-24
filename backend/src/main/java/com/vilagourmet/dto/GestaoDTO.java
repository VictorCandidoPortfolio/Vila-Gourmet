package com.vilagourmet.dto;

import com.vilagourmet.util.SenhaSegura;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class GestaoDTO {

    private GestaoDTO() {
    }

    public record PermissaoResponse(Long id, String codigo, String descricao) {
    }

    public record PerfilRequest(@NotBlank @Size(max = 80) String nome, @Size(max = 255) String descricao,
                                Boolean ativo, @NotNull Set<Long> permissaoIds) {
    }

    public record PerfilResponse(Long id, String nome, String descricao, boolean ativo, List<PermissaoResponse> permissoes) {
    }

    /** {@code senha} é obrigatória na criação e opcional na edição (redefinição). */
    public record FuncionarioRequest(@NotBlank @Email @Size(max = 254) String email,
                                     @Size(min = 8, max = 72) @Pattern(regexp = SenhaSegura.REGEX, message = SenhaSegura.MENSAGEM) String senha,
                                     @NotBlank @Size(max = 150) String nome, @NotNull Set<Long> perfilIds, Boolean ativa) {
    }

    public record FuncionarioResponse(Long id, String nome, String email, boolean ativa, List<String> perfis) {
    }

    /**
     * Com {@code cidadeIbgeId}, a cidade e a UF são as oficiais do IBGE (o servidor ignora o que vier em {@code cidade}).
     */
    public record AreaEntregaRequest(@NotBlank @Size(max = 100) String bairro, @NotBlank @Size(max = 100) String cidade,
                                     Boolean ativa, Integer cidadeIbgeId) {
    }

    public record AreaEntregaResponse(Long id, String bairro, String cidade, boolean ativa,
                                      Integer cidadeIbgeId, String uf, String origem) {
    }

    public record MunicipioResponse(int ibgeId, String nome, String uf) {
    }

    public record ImportarCidadeRequest(@NotNull Integer ibgeId) {
    }

    /** {@code ignorados}: nomes da fonte descartados por serem inválidos (vazios ou longos demais). */
    public record ImportacaoCidadeResponse(int ibgeId, String cidade, String uf, int encontrados, int adicionados,
                                           int jaExistentes, int ignorados, String fonte) {
    }

    public record TaxaEntregaRequest(@NotNull @DecimalMin("0.00") BigDecimal taxaEntrega) {
    }

    public record TaxaEntregaResponse(BigDecimal taxaEntrega) {
    }

    public record AreaConsultaResponse(boolean atendida, BigDecimal taxaEntrega) {
    }
}
