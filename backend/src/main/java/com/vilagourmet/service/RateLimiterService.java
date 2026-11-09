package com.vilagourmet.service;

import com.vilagourmet.config.AppProperties;
import com.vilagourmet.exception.NegocioException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Limitador de tentativas em memória (janela fixa por chave), usado contra enumeração de pedidos,
 * contas de mesa e força bruta de login. Em múltiplas instâncias, substituir por um armazenamento compartilhado.
 */
@Service
public class RateLimiterService {

    private record Janela(Instant inicio, int falhas) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final int maximo;
    private final Duration duracao;

    public RateLimiterService(AppProperties props) {
        this.maximo = props.rateLimit().maxTentativas();
        this.duracao = Duration.ofMinutes(props.rateLimit().janelaMinutos());
    }

    /** Lança 429 se a chave já esgotou as tentativas na janela atual. */
    public void exigirDisponivel(String chave) {
        Janela j = janelas.get(chave);
        if (j != null && !expirada(j) && j.falhas() >= maximo) {
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde alguns minutos.");
        }
    }

    public void registrarFalha(String chave) {
        Instant agora = Instant.now();
        janelas.merge(chave, new Janela(agora, 1),
                (atual, novo) -> expirada(atual) ? novo : new Janela(atual.inicio(), atual.falhas() + 1));
    }

    public void limpar(String chave) {
        janelas.remove(chave);
    }

    private boolean expirada(Janela j) {
        return j.inicio().plus(duracao).isBefore(Instant.now());
    }
}
