package com.vilagourmet.controller;

import com.vilagourmet.dto.AuthDTO.CadastroClienteRequest;
import com.vilagourmet.dto.AuthDTO.ConfiguracaoInicialResponse;
import com.vilagourmet.dto.AuthDTO.LoginClienteRequest;
import com.vilagourmet.dto.AuthDTO.LoginRequest;
import com.vilagourmet.dto.AuthDTO.PrimeiroAcessoRequest;
import com.vilagourmet.dto.AuthDTO.TokenResponse;
import com.vilagourmet.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/cliente/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse cadastrar(@Valid @RequestBody CadastroClienteRequest req) {
        return auth.cadastrarCliente(req);
    }

    @PostMapping("/cliente/login")
    public TokenResponse loginCliente(@Valid @RequestBody LoginClienteRequest req, HttpServletRequest http) {
        return auth.loginCliente(req, http.getRemoteAddr());
    }

    @PostMapping("/funcionario/login")
    public TokenResponse loginFuncionario(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.loginFuncionario(req, http.getRemoteAddr());
    }

    @GetMapping("/funcionario/configuracao-inicial")
    public ConfiguracaoInicialResponse configuracaoInicial() {
        return auth.configuracaoInicial();
    }

    @PostMapping("/funcionario/primeiro-acesso")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse primeiroAcesso(@Valid @RequestBody PrimeiroAcessoRequest req) {
        return auth.primeiroAcesso(req);
    }
}
