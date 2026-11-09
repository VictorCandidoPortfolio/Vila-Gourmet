package com.vilagourmet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vilagourmet.config.AppProperties;
import com.vilagourmet.dto.CotacaoDTO.ItemCotado;
import com.vilagourmet.model.enums.TipoUsuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emite e valida JWTs assinados (HS256). Dois tipos de token, distinguidos pela claim "typ":
 * "auth" (autenticação) e "cotacao" (preço garantido durante a sessão de montagem).
 * Os tokens nunca carregam senha ou dados pessoais: apenas id, tipo de usuário e a cotação.
 */
@Service
public class JwtService {

    private static final String TIPO_AUTH = "auth";
    private static final String TIPO_COTACAO = "cotacao";

    public record TokenAuth(Long id, TipoUsuario tipo) {
    }

    public record CotacaoEmitida(String token, Instant expiraEm) {
    }

    private final SecretKey chave;
    private final AppProperties props;
    private final ObjectMapper mapper;

    public JwtService(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        // exige >= 256 bits; falha na inicialização se o segredo for fraco
        this.chave = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public long expiracaoSegundos() {
        return Duration.ofMinutes(props.jwt().expiracaoMinutos()).toSeconds();
    }

    public String gerarTokenAutenticacao(Long id, TipoUsuario tipo) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("typ", TIPO_AUTH)
                .claim("tipo", tipo.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(expiracaoSegundos())))
                .signWith(chave)
                .compact();
    }

    public Optional<TokenAuth> lerTokenAutenticacao(String token) {
        return ler(token, TIPO_AUTH).map(c -> {
            try {
                return new TokenAuth(Long.valueOf(c.getSubject()), TipoUsuario.valueOf(c.get("tipo", String.class)));
            } catch (RuntimeException e) {
                return null;
            }
        });
    }

    public CotacaoEmitida emitirCotacao(ItemCotado item) {
        Instant agora = Instant.now();
        Instant expira = agora.plus(Duration.ofMinutes(props.jwt().cotacaoMinutos()));
        try {
            String token = Jwts.builder()
                    .subject("cotacao")
                    .claim("typ", TIPO_COTACAO)
                    .claim("item", mapper.writeValueAsString(item))
                    .issuedAt(Date.from(agora))
                    .expiration(Date.from(expira))
                    .signWith(chave)
                    .compact();
            return new CotacaoEmitida(token, expira);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar cotação", e);
        }
    }

    public Optional<ItemCotado> lerCotacao(String token) {
        return ler(token, TIPO_COTACAO).map(c -> {
            try {
                return mapper.readValue(c.get("item", String.class), ItemCotado.class);
            } catch (JsonProcessingException | RuntimeException e) {
                return null;
            }
        });
    }

    private Optional<Claims> ler(String token, String tipoEsperado) {
        try {
            Claims claims = Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
            return tipoEsperado.equals(claims.get("typ", String.class)) ? Optional.of(claims) : Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
