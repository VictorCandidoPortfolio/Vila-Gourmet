package com.vilagourmet.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriedades da aplicação (prefixo "app" em application.properties). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Codigos codigos, RateLimit rateLimit, Cloudinary cloudinary) {

    public record Jwt(String secret, long expiracaoMinutos, long cotacaoMinutos) {
    }

    public record Cors(List<String> origens) {
    }

    public record Codigos(long confirmacaoValidadeHoras, int confirmacaoMaxTentativas) {
    }

    public record RateLimit(int maxTentativas, long janelaMinutos) {
    }

    /**
     * Credenciais do Cloudinary: informe CLOUDINARY_URL (cloudinary://chave:segredo@nome-da-conta) ou
     * cloud-name + api-key + api-secret. {@code baseUrl} só muda em testes.
     */
    public record Cloudinary(String url, String cloudName, String apiKey, String apiSecret, String baseUrl,
                             String folder, long maxBytes) {
    }
}
