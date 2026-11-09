package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vilagourmet.dto.GestaoDTO.MunicipioResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Um servidor HTTP local faz o papel da API de localidades do IBGE (formato view=nivelado). */
class IbgeServiceTest {

    private HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private volatile int status = 200;

    private static String m(int id, String nome, String uf) {
        return "{\"municipio-id\":" + id + ",\"municipio-nome\":\"" + nome + "\",\"UF-sigla\":\"" + uf + "\"}";
    }

    private final String corpo = "[" + String.join(",",
            m(3538709, "Piracicaba", "SP"), m(3550308, "São Paulo", "SP"), m(2110005, "Santa Luzia", "MA"),
            m(3157807, "Santa Luzia", "MG"), m(2506509, "Santa Luzia", "PB"), m(2928950, "Santa Luzia", "BA"),
            m(1506559, "Santa Luzia do Pará", "PA"), m(3548500, "Santos", "SP"), m(4314902, "Porto Alegre", "RS")) + "]";

    @BeforeEach
    void subir() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            chamadas.incrementAndGet();
            byte[] resp = corpo.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private IbgeService servico() {
        return new IbgeService("http://127.0.0.1:" + servidor.getAddress().getPort() + "/localidades", new ObjectMapper());
    }

    @Test
    void pesquisaSemAcentoNemMaiusculasMostrandoNomeEUf() {
        List<MunicipioResponse> r = servico().pesquisar("SAO pau");
        assertThat(r).extracting(MunicipioResponse::nome).containsExactly("São Paulo");
        assertThat(r.get(0).uf()).isEqualTo("SP");
        assertThat(r.get(0).ibgeId()).isEqualTo(3550308);
    }

    @Test
    void nomeCompletoComHomonimosTrazTodasAsUfsEOMatchExatoAntesDosParecidos() {
        List<MunicipioResponse> r = servico().pesquisar("santa luzia");
        assertThat(r).extracting(x -> x.nome() + "/" + x.uf())
                .containsExactly("Santa Luzia/BA", "Santa Luzia/MA", "Santa Luzia/MG", "Santa Luzia/PB", "Santa Luzia do Pará/PA");
    }

    @Test
    void buscaCurtaOuVaziaNaoConsultaNada() {
        assertThat(servico().pesquisar("p")).isEmpty();
        assertThat(servico().pesquisar("  ")).isEmpty();
        assertThat(servico().pesquisar(null)).isEmpty();
        assertThat(chamadas.get()).isZero();
    }

    @Test
    void listaEBaixadaUmaVezEFicaEmMemoria() {
        IbgeService s = servico();
        s.pesquisar("sant");
        s.pesquisar("porto");
        s.buscarPorId(3538709);
        assertThat(chamadas.get()).isEqualTo(1);
    }

    @Test
    void codigoIbgeInexistenteEhRejeitado() {
        assertThat(servico().buscarPorId(3538709).nome()).isEqualTo("Piracicaba");
        assertThatThrownBy(() -> servico().buscarPorId(1)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void ibgeForaDoArVira502() {
        status = 503;
        assertThatThrownBy(() -> servico().pesquisar("piracicaba")).isInstanceOf(NegocioException.class)
                .hasMessageContaining("IBGE");
    }
}
