package com.vilagourmet.security;

import com.vilagourmet.model.enums.TipoUsuario;

/**
 * Principal da requisição autenticada. {@code id} é o id de Cliente ou de Funcionario,
 * conforme o {@code tipo}.
 */
public record UsuarioAutenticado(Long id, TipoUsuario tipo, String email) {

    public boolean ehCliente() {
        return tipo == TipoUsuario.CLIENTE;
    }

    public boolean ehFuncionario() {
        return tipo == TipoUsuario.FUNCIONARIO;
    }
}
