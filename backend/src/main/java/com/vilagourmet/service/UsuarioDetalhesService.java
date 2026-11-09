package com.vilagourmet.service;

import com.vilagourmet.model.Cliente;
import com.vilagourmet.model.Funcionario;
import com.vilagourmet.model.Perfil;
import com.vilagourmet.model.Permissao;
import com.vilagourmet.model.enums.TipoUsuario;
import com.vilagourmet.repository.ClienteRepository;
import com.vilagourmet.repository.FuncionarioRepository;
import com.vilagourmet.security.UsuarioAutenticado;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolve o usuário de um token válido e suas autoridades (papel + permissões do perfil). */
@Service
public class UsuarioDetalhesService {

    public record Detalhes(UsuarioAutenticado principal, List<GrantedAuthority> autoridades) {
    }

    private final ClienteRepository clientes;
    private final FuncionarioRepository funcionarios;

    public UsuarioDetalhesService(ClienteRepository clientes, FuncionarioRepository funcionarios) {
        this.clientes = clientes;
        this.funcionarios = funcionarios;
    }

    @Transactional(readOnly = true)
    public Optional<Detalhes> carregar(JwtService.TokenAuth token) {
        if (token.tipo() == TipoUsuario.CLIENTE) {
            return clientes.findById(token.id())
                    .filter(c -> c.getConta().isAtiva())
                    .map(this::detalhesCliente);
        }
        return funcionarios.findById(token.id())
                .filter(f -> f.getConta().isAtiva())
                .map(this::detalhesFuncionario);
    }

    private Detalhes detalhesCliente(Cliente c) {
        return new Detalhes(new UsuarioAutenticado(c.getId(), TipoUsuario.CLIENTE, c.getConta().getEmail()),
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
    }

    private Detalhes detalhesFuncionario(Funcionario f) {
        List<GrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority("ROLE_FUNCIONARIO"));
        for (Perfil perfil : f.getPerfis()) {
            if (perfil.isAtivo()) {
                for (Permissao p : perfil.getPermissoes()) {
                    autoridades.add(new SimpleGrantedAuthority(p.getCodigo()));
                }
            }
        }
        return new Detalhes(new UsuarioAutenticado(f.getId(), TipoUsuario.FUNCIONARIO, f.getConta().getEmail()), autoridades);
    }

    /** Códigos de permissão efetivos do funcionário (perfis ativos). */
    @Transactional(readOnly = true)
    public List<String> permissoesDoFuncionario(Long funcionarioId) {
        return carregar(new JwtService.TokenAuth(funcionarioId, TipoUsuario.FUNCIONARIO))
                .map(d -> d.autoridades().stream().map(GrantedAuthority::getAuthority)
                        .filter(a -> !a.startsWith("ROLE_")).distinct().sorted().toList())
                .orElse(List.of());
    }
}
