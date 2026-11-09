package com.vilagourmet.exception;

public class NaoEncontradoException extends NegocioException {

    public NaoEncontradoException(String mensagem) {
        super(org.springframework.http.HttpStatus.NOT_FOUND, mensagem);
    }
}
