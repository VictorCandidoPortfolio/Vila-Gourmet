package com.vilagourmet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vilagourmet.config.AppProperties;
import com.vilagourmet.dto.CatalogoDTO.ImagemResponse;
import com.vilagourmet.exception.NegocioException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Recebe a foto em base64, valida (tipo real pelos primeiros bytes e tamanho), envia assinada ao
 * Cloudinary e devolve a URL. Só a URL é gravada no banco. O segredo da API nunca sai do servidor.
 */
@Service
public class ImagemService {

    private static final Logger log = LoggerFactory.getLogger(ImagemService.class);
    private static final Set<String> PASTAS = Set.of("produtos", "insumos");

    private record Credenciais(String cloud, String chave, String segredo) {
    }

    private final AppProperties.Cloudinary props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ImagemService(AppProperties props, ObjectMapper mapper) {
        this.props = props.cloudinary();
        this.mapper = mapper;
    }

    /** Envia a imagem para a pasta ("produtos" ou "insumos") e devolve a URL segura. */
    public ImagemResponse enviar(String base64, String pasta) {
        Credenciais cred = credenciais();
        byte[] bytes = decodificar(base64);
        String mime = tipoDaImagem(bytes);
        String pastaFinal = props.folder() + "/" + (PASTAS.contains(pasta) ? pasta : "geral");

        Map<String, String> assinados = new TreeMap<>();
        assinados.put("folder", pastaFinal);
        assinados.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));

        Map<String, String> form = new TreeMap<>(assinados);
        form.put("file", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes));
        form.put("api_key", cred.chave());
        form.put("signature", assinar(assinados, cred.segredo()));

        String corpo = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/v1_1/" + cred.cloud() + "/image/upload"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Cloudinary respondeu {}: {}", resp.statusCode(), resp.body());
                throw new NegocioException(HttpStatus.BAD_GATEWAY, "Não foi possível enviar a imagem ao serviço de imagens. Tente novamente.");
            }
            JsonNode json = mapper.readTree(resp.body());
            String url = json.path("secure_url").asText("");
            if (url.isBlank()) {
                throw new NegocioException(HttpStatus.BAD_GATEWAY, "O serviço de imagens não retornou a URL da imagem.");
            }
            return new ImagemResponse(url, json.path("public_id").asText(null));
        } catch (IOException e) {
            log.warn("Falha de comunicação com o Cloudinary", e);
            throw new NegocioException(HttpStatus.BAD_GATEWAY, "Não foi possível se comunicar com o serviço de imagens.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NegocioException(HttpStatus.BAD_GATEWAY, "Envio da imagem interrompido.");
        }
    }

    /** Assinatura do Cloudinary: SHA-1 de "k=v&k=v" (chaves em ordem alfabética) + segredo da API. */
    static String assinar(Map<String, String> parametros, String segredo) {
        String base = new TreeMap<>(parametros).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest((base + segredo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] decodificar(String base64) {
        String dados = base64 == null ? "" : base64.trim();
        int virgula = dados.indexOf(',');
        if (dados.startsWith("data:") && virgula > 0) {
            dados = dados.substring(virgula + 1);
        }
        dados = dados.replaceAll("\\s", "");
        if (dados.isEmpty()) {
            throw new NegocioException("Nenhuma imagem foi enviada");
        }
        // o tamanho é conferido antes de decodificar, para não alocar memória à toa
        if ((long) dados.length() * 3 / 4 > props.maxBytes() + 3) {
            throw tamanhoExcedido();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(dados);
            if (bytes.length > props.maxBytes()) {
                throw tamanhoExcedido();
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new NegocioException("Imagem inválida (base64 malformado)");
        }
    }

    private NegocioException tamanhoExcedido() {
        return new NegocioException(HttpStatus.PAYLOAD_TOO_LARGE, "A imagem excede o limite de " + (props.maxBytes() / 1024 / 1024) + " MB");
    }

    /** Identifica o tipo pelos primeiros bytes (não confia no prefixo informado pelo cliente). */
    static String tipoDaImagem(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        throw new NegocioException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de imagem não suportado. Use PNG, JPEG, WEBP ou GIF.");
    }

    private Credenciais credenciais() {
        if (props.url() != null && props.url().startsWith("cloudinary://")) {
            // cloudinary://<chave>:<segredo>@<nome-da-conta>
            try {
                URI uri = URI.create(props.url());
                String[] usuario = uri.getUserInfo().split(":", 2);
                return new Credenciais(uri.getHost(), usuario[0], usuario[1]);
            } catch (RuntimeException e) {
                throw new IllegalStateException("CLOUDINARY_URL inválida (esperado cloudinary://chave:segredo@conta)");
            }
        }
        if (vazio(props.cloudName()) || vazio(props.apiKey()) || vazio(props.apiSecret())) {
            throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O envio de imagens não está configurado no servidor: informe as credenciais do Cloudinary em application-local.properties "
                            + "(app.cloudinary.url) ou na variável de ambiente CLOUDINARY_URL.");
        }
        return new Credenciais(props.cloudName(), props.apiKey(), props.apiSecret());
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
