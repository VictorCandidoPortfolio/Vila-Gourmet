package com.vilagourmet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vilagourmet.dto.ClienteDTO.CepResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Consulta de CEP no ViaCEP (serviço gratuito e sem chave), para o front preencher rua, bairro, cidade e UF. */
@Service
public class CepService {

    private final String baseUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CepService(@Value("${app.cep.base-url:https://viacep.com.br/ws}") String baseUrl, ObjectMapper mapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.mapper = mapper;
    }

    public CepResponse consultar(String cepInformado) {
        String cep = cepInformado == null ? "" : cepInformado.replaceAll("\\D", "");
        if (cep.length() != 8) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "CEP inválido: informe os 8 dígitos");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + cep + "/json/"))
                .timeout(Duration.ofSeconds(8)).GET().build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw indisponivel();
            }
            JsonNode json = mapper.readTree(resp.body());
            if (json.path("erro").asBoolean(false) || json.path("erro").asText("").equals("true")) {
                throw new NaoEncontradoException("CEP não encontrado");
            }
            return new CepResponse(cep, texto(json, "logradouro"), texto(json, "complemento"), texto(json, "bairro"),
                    texto(json, "localidade"), texto(json, "uf"));
        } catch (IOException e) {
            throw indisponivel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw indisponivel();
        }
    }

    private static String texto(JsonNode json, String campo) {
        String v = json.path(campo).asText("").trim();
        return v.isEmpty() ? null : v;
    }

    private static NegocioException indisponivel() {
        return new NegocioException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar o CEP agora. Preencha o endereço manualmente.");
    }
}
