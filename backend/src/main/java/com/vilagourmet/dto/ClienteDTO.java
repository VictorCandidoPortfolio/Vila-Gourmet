package com.vilagourmet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class ClienteDTO {

    private ClienteDTO() {
    }

    public record ClienteResponse(Long id, String nome, String email, String cpf) {
    }

    public record EnderecoRequest(
            @Size(max = 80) String identificacao,
            @NotBlank String cep,
            @NotBlank @Size(max = 200) String logradouro,
            @NotBlank @Size(max = 20) String numero,
            @Size(max = 100) String complemento,
            @NotBlank @Size(max = 100) String bairro,
            @NotBlank @Size(max = 100) String cidade,
            @NotBlank @Size(min = 2, max = 2) String estado,
            boolean padrao) {
    }

    public record EnderecoResponse(Long id, String identificacao, String cep, String logradouro, String numero,
                                   String complemento, String bairro, String cidade, String estado, boolean padrao) {
    }

    /** Resultado da consulta de CEP; campos que o serviço não informar vêm nulos. */
    public record CepResponse(String cep, String logradouro, String complemento, String bairro, String cidade, String estado) {
    }

    public record PizzaSalvaRequest(@NotBlank @Size(max = 100) String nome, @NotNull Long produtoId,
                                    @Valid List<CotacaoDTO.SelecaoOpcao> escolhas) {
    }

    public record PizzaSalvaResponse(Long id, String nome, Long produtoId, List<CotacaoDTO.SelecaoOpcao> escolhas,
                                     LocalDateTime criadoEm) {
    }
}
