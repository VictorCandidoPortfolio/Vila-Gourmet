package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vilagourmet.dto.ClienteDTO.CepResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Um servidor HTTP local faz o papel do ViaCEP. */
class CepServiceTest {

    private HttpServer servidor;
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String corpo = "{\"cep\":\"01001-000\",\"logradouro\":\"Praça da Sé\",\"complemento\":\"lado ímpar\",\"bairro\":\"Sé\",\"localidade\":\"São Paulo\",\"uf\":\"SP\"}";

    @BeforeEach
    void subir() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            caminho.set(ex.getRequestURI().getPath());
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

    private CepService servico() {
        return new CepService("http://127.0.0.1:" + servidor.getAddress().getPort() + "/ws/", new ObjectMapper());
    }

    @Test
    void preencheEnderecoAPartirDoCepComOuSemMascara() {
        CepResponse r = servico().consultar("01001-000");
        assertThat(caminho.get()).isEqualTo("/ws/01001000/json/");
        assertThat(r.cep()).isEqualTo("01001000");
        assertThat(r.logradouro()).isEqualTo("Praça da Sé");
        assertThat(r.bairro()).isEqualTo("Sé");
        assertThat(r.cidade()).isEqualTo("São Paulo");
        assertThat(r.estado()).isEqualTo("SP");
    }

    @Test
    void cepGenericoVemSemLogradouro() {
        corpo = "{\"cep\":\"13400-000\",\"logradouro\":\"\",\"complemento\":\"\",\"bairro\":\"\",\"localidade\":\"Piracicaba\",\"uf\":\"SP\"}";
        CepResponse r = servico().consultar("13400000");
        assertThat(r.logradouro()).isNull();
        assertThat(r.cidade()).isEqualTo("Piracicaba");
    }

    @Test
    void rejeitaCepMalFormado() {
        assertThatThrownBy(() -> servico().consultar("123")).isInstanceOf(NegocioException.class).hasMessageContaining("CEP inválido");
        assertThatThrownBy(() -> servico().consultar(null)).isInstanceOf(NegocioException.class);
    }

    @Test
    void cepInexistente() {
        corpo = "{\"erro\":true}";
        assertThatThrownBy(() -> servico().consultar("99999999")).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void falhaDoServicoViraBadGateway() {
        status = 500;
        assertThatThrownBy(() -> servico().consultar("01001000")).isInstanceOf(NegocioException.class)
                .hasMessageContaining("Não foi possível consultar o CEP");
    }
}
