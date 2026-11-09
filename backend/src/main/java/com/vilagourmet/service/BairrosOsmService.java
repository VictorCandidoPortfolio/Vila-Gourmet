package com.vilagourmet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.util.Textos;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Bairros de um município a partir do OpenStreetMap (API Overpass, gratuita). O IBGE não publica bairros por API:
 * aqui o município é localizado pela etiqueta {@code IBGE:GEOCODIGO} das fronteiras mapeadas no OSM e são lidos
 * os lugares {@code place=suburb|neighbourhood|quarter} dentro dele. A cobertura varia muito: boa em capitais e
 * cidades médias, e vazia em muitos municípios pequenos. Dados © colaboradores do OpenStreetMap (licença ODbL).
 */
@Service
public class BairrosOsmService {

    public static final String FONTE = "OpenStreetMap (Overpass API)";
    static final int TAMANHO_MAXIMO_NOME = 100;
    private static final Logger log = LoggerFactory.getLogger(BairrosOsmService.class);

    /** Nomes aproveitáveis (sem repetição) e quantos foram descartados por serem inválidos. */
    public record Resultado(List<String> nomes, int ignorados) {
    }

    private final List<String> servidores;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public BairrosOsmService(
            @Value("${app.bairros.overpass-urls:https://overpass-api.de/api/interpreter,https://overpass.kumi.systems/api/interpreter}") List<String> servidores,
            @Value("${app.bairros.timeout-segundos:90}") long timeoutSegundos,
            ObjectMapper mapper) {
        this.servidores = servidores.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.timeout = Duration.ofSeconds(timeoutSegundos);
        this.mapper = mapper;
    }

    /** Lista vazia = o OSM não tem bairros mapeados para o município. Falha de todos os servidores = erro 502. */
    public Resultado buscar(int ibgeId) {
        String consulta = "[out:json][timeout:60];\n"
                + "area[\"boundary\"=\"administrative\"][\"admin_level\"=\"8\"][\"IBGE:GEOCODIGO\"=\"" + ibgeId + "\"]->.a;\n"
                + "nwr(area.a)[\"place\"~\"^(suburb|neighbourhood|quarter)$\"][\"name\"];\n"
                + "out tags;";
        String corpo = "data=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8);

        for (String servidor : servidores) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(servidor)).timeout(timeout)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", "VilaGourmet/1.0 (importacao de bairros da area de entrega)")
                        .POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() / 100 != 2) {
                    log.warn("Overpass {} respondeu {}", servidor, resp.statusCode());
                    continue;
                }
                JsonNode json = mapper.readTree(resp.body());
                JsonNode elementos = json.path("elements");
                String aviso = json.path("remark").asText("");
                if (elementos.isEmpty() && aviso.toLowerCase().contains("error")) { // consulta abortada no servidor
                    log.warn("Overpass {} abortou a consulta: {}", servidor, aviso);
                    continue;
                }
                return extrair(elementos);
            } catch (IOException e) {
                log.warn("Falha ao consultar o Overpass {}: {}", servidor, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new NegocioException(HttpStatus.BAD_GATEWAY,
                "O serviço de bairros (OpenStreetMap) está indisponível ou demorou demais. Tente novamente em alguns minutos ou cadastre os bairros manualmente.");
    }

    static Resultado extrair(JsonNode elementos) {
        Map<String, String> unicos = new LinkedHashMap<>();
        int ignorados = 0;
        for (JsonNode e : elementos) {
            String nome = e.path("tags").path("name").asText("").trim().replaceAll("\\s+", " ");
            if (nome.isEmpty() || nome.length() > TAMANHO_MAXIMO_NOME) {
                ignorados++;
                continue;
            }
            unicos.putIfAbsent(Textos.normalizar(nome), nome);
        }
        List<String> nomes = new ArrayList<>(unicos.values());
        nomes.sort((a, b) -> Textos.normalizar(a).compareTo(Textos.normalizar(b)));
        return new Resultado(nomes, ignorados);
    }
}
