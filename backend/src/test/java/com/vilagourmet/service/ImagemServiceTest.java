package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vilagourmet.config.AppProperties;
import com.vilagourmet.dto.CatalogoDTO.ImagemResponse;
import com.vilagourmet.exception.NegocioException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Usa um servidor HTTP local no lugar do Cloudinary para conferir a requisição assinada. */
class ImagemServiceTest {

    // PNG mínimo válido (1x1 pixel)
    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private HttpServer servidor;
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> campos = new AtomicReference<>();
    private volatile int statusResposta = 200;
    private volatile String corpoResposta =
            "{\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/v1/vila-gourmet/produtos/a.png\",\"public_id\":\"vila-gourmet/produtos/a\"}";

    @BeforeEach
    void subir() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            caminho.set(ex.getRequestURI().getPath());
            Map<String, String> m = new LinkedHashMap<>();
            for (String par : new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
                String[] kv = par.split("=", 2);
                m.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
            }
            campos.set(m);
            byte[] resp = corpoResposta.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(statusResposta, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private ImagemService servico(String url, String cloud, String chave, String segredo, long max) {
        AppProperties.Cloudinary c = new AppProperties.Cloudinary(url, cloud, chave, segredo,
                "http://127.0.0.1:" + servidor.getAddress().getPort(), "vila-gourmet", max);
        return new ImagemService(new AppProperties(null, null, null, null, c), new ObjectMapper());
    }

    private ImagemService configurado() {
        return servico("", "demo", "chave123", "segredo456", 5_242_880);
    }

    @Test
    void enviaAssinadoERetornaAUrl() {
        ImagemResponse r = configurado().enviar("data:image/png;base64," + PNG_1X1, "produtos");

        assertThat(r.url()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/vila-gourmet/produtos/a.png");
        assertThat(r.publicId()).isEqualTo("vila-gourmet/produtos/a");
        assertThat(caminho.get()).isEqualTo("/v1_1/demo/image/upload");

        Map<String, String> f = campos.get();
        assertThat(f.get("api_key")).isEqualTo("chave123");
        assertThat(f.get("folder")).isEqualTo("vila-gourmet/produtos");
        assertThat(f.get("file")).isEqualTo("data:image/png;base64," + PNG_1X1);
        // assinatura = sha1("folder=...&timestamp=..." + segredo); o segredo nunca é enviado
        assertThat(f.get("signature")).isEqualTo(ImagemService.assinar(Map.of("folder", f.get("folder"), "timestamp", f.get("timestamp")), "segredo456"));
        assertThat(f.values()).doesNotContain("segredo456");
    }

    @Test
    void aceitaBase64SemPrefixoEPastaDesconhecidaVaiParaGeral() {
        configurado().enviar(PNG_1X1, "qualquer");
        assertThat(campos.get().get("folder")).isEqualTo("vila-gourmet/geral");
    }

    @Test
    void usaCloudinaryUrlQuandoInformada() {
        servico("cloudinary://k999:s888@minhaconta", "", "", "", 5_242_880).enviar(PNG_1X1, "insumos");
        assertThat(caminho.get()).isEqualTo("/v1_1/minhaconta/image/upload");
        assertThat(campos.get().get("api_key")).isEqualTo("k999");
    }

    @Test
    void assinaturaSegueOAlgoritmoDoCloudinary() {
        // valor calculado fora do Java: sha1("folder=vila-gourmet/produtos&timestamp=1315060510" + "abcd")
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timestamp", "1315060510");
        params.put("folder", "vila-gourmet/produtos"); // fora de ordem de propósito: a assinatura ordena as chaves
        assertThat(ImagemService.assinar(params, "abcd")).isEqualTo("a83aa6658fd9b181943ebf7e4c51cfe787fdc029");
    }

    @Test
    void naoConfiguradoRetorna503() {
        assertThatThrownBy(() -> servico("", "", "", "", 5_242_880).enviar(PNG_1X1, "produtos"))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.getStatus().value()).isEqualTo(503));
        assertThat(caminho.get()).isNull();
    }

    @Test
    void rejeitaArquivoQueNaoEImagemMesmoComPrefixoDeImagem() {
        String texto = Base64.getEncoder().encodeToString("isto nao e uma imagem".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> configurado().enviar("data:image/png;base64," + texto, "produtos"))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.getStatus().value()).isEqualTo(415));
        assertThat(caminho.get()).isNull();
    }

    @Test
    void rejeitaBase64MalformadoEVazio() {
        assertThatThrownBy(() -> configurado().enviar("%%%nao-e-base64%%%", "produtos")).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> configurado().enviar("   ", "produtos")).isInstanceOf(NegocioException.class);
        assertThat(caminho.get()).isNull();
    }

    @Test
    void rejeitaImagemMaiorQueOLimite() {
        assertThatThrownBy(() -> servico("", "demo", "k", "s", 50).enviar(PNG_1X1, "produtos"))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.getStatus().value()).isEqualTo(413));
        assertThat(caminho.get()).isNull();
    }

    @Test
    void falhaDoCloudinaryViraBadGateway() {
        statusResposta = 401;
        corpoResposta = "{\"error\":{\"message\":\"Invalid Signature\"}}";
        assertThatThrownBy(() -> configurado().enviar(PNG_1X1, "produtos"))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.getStatus().value()).isEqualTo(502));
    }
}
