package com.vilagourmet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vilagourmet.dto.GestaoDTO.MunicipioResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.util.Textos;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Municípios brasileiros pela API de localidades do IBGE (https://servicodados.ibge.gov.br/api/docs/localidades).
 * A lista completa (~5,5 mil municípios) é carregada uma vez e mantida em memória por 24 h; a pesquisa é feita aqui,
 * sem acentos, e o código IBGE enviado pelo cliente é sempre revalidado contra essa lista.
 */
@Service
public class IbgeService {

    private static final Duration VALIDADE = Duration.ofHours(24);
    static final int LIMITE_SUGESTOES = 15;

    private record Municipio(int id, String nome, String uf, String nomeNormalizado) {
        MunicipioResponse paraResponse() {
            return new MunicipioResponse(id, nome, uf);
        }
    }

    private record Cache(List<Municipio> lista, Map<Integer, Municipio> porId, Instant carregadoEm) {
    }

    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private volatile Cache cache;

    public IbgeService(@Value("${app.ibge.base-url:https://servicodados.ibge.gov.br/api/v1/localidades}") String baseUrl,
                       ObjectMapper mapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.mapper = mapper;
    }

    public List<MunicipioResponse> pesquisar(String busca) {
        String termo = Textos.normalizar(busca);
        if (termo.length() < 2) {
            return List.of();
        }
        return carregar().lista().stream()
                .filter(m -> m.nomeNormalizado().contains(termo))
                .sorted(Comparator
                        .comparingInt((Municipio m) -> m.nomeNormalizado().equals(termo) ? 0 : m.nomeNormalizado().startsWith(termo) ? 1 : 2)
                        .thenComparing(Municipio::nomeNormalizado).thenComparing(Municipio::uf))
                .limit(LIMITE_SUGESTOES)
                .map(Municipio::paraResponse)
                .toList();
    }

    public MunicipioResponse buscarPorId(int ibgeId) {
        Municipio m = carregar().porId().get(ibgeId);
        if (m == null) {
            throw new NaoEncontradoException("Município não encontrado no IBGE: " + ibgeId);
        }
        return m.paraResponse();
    }

    private Cache carregar() {
        Cache atual = cache;
        if (atual != null && atual.carregadoEm().plus(VALIDADE).isAfter(Instant.now())) {
            return atual;
        }
        synchronized (this) {
            atual = cache;
            if (atual != null && atual.carregadoEm().plus(VALIDADE).isAfter(Instant.now())) {
                return atual;
            }
            try {
                cache = baixar();
                return cache;
            } catch (NegocioException e) {
                if (atual != null) { // serviço fora do ar: segue com a lista antiga em vez de falhar
                    return atual;
                }
                throw e;
            }
        }
    }

    private Cache baixar() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/municipios?view=nivelado"))
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json").GET().build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw indisponivel();
            }
            List<Municipio> lista = new ArrayList<>();
            for (JsonNode n : mapper.readTree(resp.body())) {
                String nome = n.path("municipio-nome").asText("").trim();
                String uf = n.path("UF-sigla").asText("").trim();
                if (n.hasNonNull("municipio-id") && !nome.isEmpty() && uf.length() == 2) {
                    lista.add(new Municipio(n.get("municipio-id").asInt(), nome, uf, Textos.normalizar(nome)));
                }
            }
            if (lista.isEmpty()) {
                throw indisponivel();
            }
            return new Cache(lista, lista.stream().collect(Collectors.toMap(Municipio::id, m -> m, (a, b) -> a)), Instant.now());
        } catch (IOException e) {
            throw indisponivel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw indisponivel();
        }
    }

    private static NegocioException indisponivel() {
        return new NegocioException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar a lista de cidades do IBGE agora. Tente novamente em instantes.");
    }
}
