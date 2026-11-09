package com.vilagourmet.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Violação de regra de negócio (HTTP 422 por padrão). */
public class NegocioException extends RuntimeException {

    private final HttpStatus status;
    private final List<String> detalhes;

    public NegocioException(String mensagem) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, mensagem, List.of());
    }

    public NegocioException(String mensagem, List<String> detalhes) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, mensagem, detalhes);
    }

    public NegocioException(HttpStatus status, String mensagem) {
        this(status, mensagem, List.of());
    }

    public NegocioException(HttpStatus status, String mensagem, List<String> detalhes) {
        super(mensagem);
        this.status = status;
        this.detalhes = detalhes;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<String> getDetalhes() {
        return detalhes;
    }
}
