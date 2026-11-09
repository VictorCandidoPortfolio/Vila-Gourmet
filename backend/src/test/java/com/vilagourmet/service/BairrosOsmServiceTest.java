package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vilagourmet.exception.NegocioException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Dois servidores HTTP locais fazem o papel dos servidores Overpass (principal e reserva). */
class BairrosOsmServiceTest {

    private HttpServer principal;
    private HttpServer reserva;
    private final AtomicReference<String> consultaRecebida = new AtomicReference<>();
    private volatile int statusPrincipal = 200;
    private volatile String corpoPrincipal = "{\"elements\":[]}";
    private volatile int statusReserva = 200;
    private volatile String corpoReserva = "{\"elements\":[]}";

    private static HttpServer criar(java.util.function.Supplier<Integer> status, java.util.function.Supplier<String> corpo,
                                    AtomicReference<String> consulta) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/api/interpreter", ex -> {
            String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (consulta != null) {
                consulta.set(URLDecoder.decode(form, StandardCharsets.UTF_8));
            }
            byte[] resp = corpo.get().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status.get(), resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        s.start();
        return s;
    }

    @BeforeEach
    void subir() throws Exception {
        principal = criar(() -> statusPrincipal, () -> corpoPrincipal, consultaRecebida);
        reserva = criar(() -> statusReserva, () -> corpoReserva, null);
    }

    @AfterEach
    void parar() {
        principal.stop(0);
        reserva.stop(0);
    }

    private static String url(HttpServer s) {
        return "http://127.0.0.1:" + s.getAddress().getPort() + "/api/interpreter";
    }

    private BairrosOsmService servico() {
        return new BairrosOsmService(List.of(url(principal), url(reserva)), 10, new ObjectMapper());
    }

    private static String el(String nome) {
        return "{\"type\":\"node\",\"tags\":{\"name\":\"" + nome + "\",\"place\":\"suburb\"}}";
    }

    @Test
    void consultaOMunicipioPeloCodigoIbgeELeBairrosSemRepeticao() {
        corpoPrincipal = "{\"elements\":[" + String.join(",", el("Centro"), el("Água Branca"), el("CENTRO"), el("agua branca"), el("Alemães"),
                el("  "), el("N".repeat(101))) + "]}";
        BairrosOsmService.Resultado r = servico().buscar(3538709);
        assertThat(consultaRecebida.get()).contains("\"IBGE:GEOCODIGO\"=\"3538709\"").contains("suburb|neighbourhood|quarter");
        assertThat(r.nomes()).containsExactly("Água Branca", "Alemães", "Centro");
        assertThat(r.ignorados()).isEqualTo(2);
    }

    @Test
    void municipioSemBairrosMapeadosVoltaVazio() {
        assertThat(servico().buscar(1100015).nomes()).isEmpty();
    }

    @Test
    void usaOServidorReservaQuandoOPrincipalFalha() {
        statusPrincipal = 504;
        corpoReserva = "{\"elements\":[" + el("Centro") + "]}";
        assertThat(servico().buscar(3538709).nomes()).containsExactly("Centro");
    }

    @Test
    void consultaAbortadaNoServidorTambemTentaOReserva() {
        corpoPrincipal = "{\"elements\":[],\"remark\":\"runtime error: Query timed out\"}";
        corpoReserva = "{\"elements\":[" + el("Centro") + "]}";
        assertThat(servico().buscar(3538709).nomes()).containsExactly("Centro");
    }

    @Test
    void todosForaDoArViraErro502SemSimularSucesso() {
        statusPrincipal = 500;
        statusReserva = 429;
        assertThatThrownBy(() -> servico().buscar(3538709)).isInstanceOf(NegocioException.class)
                .hasMessageContaining("indisponível");
    }
}
