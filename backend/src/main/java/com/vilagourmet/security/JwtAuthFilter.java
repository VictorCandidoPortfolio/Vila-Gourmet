package com.vilagourmet.security;

import com.vilagourmet.service.JwtService;
import com.vilagourmet.service.UsuarioDetalhesService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida o JWT (Bearer) e carrega perfil/permissões do banco a cada requisição, para que
 * revogações de permissão tenham efeito imediato. Token ausente ou inválido deixa a
 * requisição anônima; rotas protegidas respondem 401.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioDetalhesService usuarios;

    public JwtAuthFilter(JwtService jwtService, UsuarioDetalhesService usuarios) {
        this.jwtService = jwtService;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho != null && cabecalho.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.lerTokenAutenticacao(cabecalho.substring(7))
                    .flatMap(usuarios::carregar)
                    .ifPresent(u -> {
                        var auth = new UsernamePasswordAuthenticationToken(u.principal(), null, u.autoridades());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
