package com.vilagourmet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDTO {

    private AuthDTO() {
    }

    public record CadastroClienteRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String senha,
            @NotBlank @Size(max = 150) String nome,
            @NotBlank String cpf) {
    }

    /**
     * Login do cliente por e-mail ou CPF. {@code login} aceita os dois; {@code email} continua aceito por
     * compatibilidade com o formato antigo.
     */
    public record LoginClienteRequest(String login, String email, @NotBlank String senha) {
        public String identificador() {
            String v = login != null && !login.isBlank() ? login : email;
            return v == null ? "" : v.trim();
        }
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {
    }

    /** Cria o primeiro administrador; só é aceito enquanto não existir nenhum funcionário. */
    public record PrimeiroAcessoRequest(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String senha) {
    }

    /** Dados atuais do funcionário logado (a UI atualiza o menu com as permissões de agora). */
    public record SessaoFuncionarioResponse(String nome, List<String> permissoes) {
    }

    public record ConfiguracaoInicialResponse(boolean primeiroAcessoNecessario) {
    }

    /** {@code permissoes} só é preenchido para funcionários (a UI usa para montar o menu; o servidor revalida). */
    public record TokenResponse(String token, String tipo, String nome, long expiraEmSegundos, List<String> permissoes) {
    }
}
