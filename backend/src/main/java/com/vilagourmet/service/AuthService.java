package com.vilagourmet.service;

import com.vilagourmet.dto.AuthDTO.CadastroClienteRequest;
import com.vilagourmet.dto.AuthDTO.ConfiguracaoInicialResponse;
import com.vilagourmet.dto.AuthDTO.LoginClienteRequest;
import com.vilagourmet.dto.AuthDTO.LoginRequest;
import com.vilagourmet.dto.AuthDTO.PrimeiroAcessoRequest;
import com.vilagourmet.dto.AuthDTO.TokenResponse;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Cliente;
import com.vilagourmet.model.ContaCliente;
import com.vilagourmet.model.ContaFuncionario;
import com.vilagourmet.model.Funcionario;
import com.vilagourmet.model.enums.TipoUsuario;
import com.vilagourmet.repository.ClienteRepository;
import com.vilagourmet.repository.ContaClienteRepository;
import com.vilagourmet.repository.ContaFuncionarioRepository;
import com.vilagourmet.repository.FuncionarioRepository;
import com.vilagourmet.util.Cpf;
import com.vilagourmet.util.SenhaSegura;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de clientes e login de clientes e funcionários. Senhas somente como hash BCrypt. */
@Service
public class AuthService {

    private final ContaClienteRepository contasCliente;
    private final ClienteRepository clientes;
    private final ContaFuncionarioRepository contasFuncionario;
    private final FuncionarioRepository funcionarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final UsuarioDetalhesService detalhes;
    private final RateLimiterService limiter;
    private final FuncionarioService funcionarioService;
    /** Hash descartável: iguala o tempo de resposta quando o e-mail não existe (evita enumeração por timing). */
    private final String hashFalso;

    public AuthService(ContaClienteRepository contasCliente, ClienteRepository clientes,
                       ContaFuncionarioRepository contasFuncionario, FuncionarioRepository funcionarios,
                       PasswordEncoder encoder, JwtService jwt, UsuarioDetalhesService detalhes,
                       RateLimiterService limiter, FuncionarioService funcionarioService) {
        this.contasCliente = contasCliente;
        this.clientes = clientes;
        this.contasFuncionario = contasFuncionario;
        this.funcionarios = funcionarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.detalhes = detalhes;
        this.limiter = limiter;
        this.funcionarioService = funcionarioService;
        this.hashFalso = encoder.encode("senha-inexistente");
    }

    @Transactional(readOnly = true)
    public ConfiguracaoInicialResponse configuracaoInicial() {
        return new ConfiguracaoInicialResponse(funcionarioService.semFuncionarios());
    }

    /**
     * Primeiro acesso: cria o administrador somente enquanto não existir nenhum funcionário.
     * Depois disso, novas contas da equipe só são criadas por quem tem a permissão FUNCIONARIO_GERENCIAR.
     * O bloqueio serializa tentativas simultâneas dentro desta instância.
     */
    public synchronized TokenResponse primeiroAcesso(PrimeiroAcessoRequest req) {
        SenhaSegura.exigirValida(req.senha());
        String email = req.email().trim().toLowerCase();
        if (contasCliente.existsByEmailIgnoreCase(email)) {
            throw new NegocioException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        if (!funcionarioService.criarAdministradorInicialSeNecessario(email, req.senha(), req.nome().trim())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "O primeiro acesso já foi realizado. Peça a um administrador para criar sua conta.");
        }
        return loginFuncionario(new LoginRequest(email, req.senha()), "primeiro-acesso");
    }

    @Transactional
    public TokenResponse cadastrarCliente(CadastroClienteRequest req) {
        SenhaSegura.exigirValida(req.senha());
        String email = req.email().trim().toLowerCase();
        String cpf = Cpf.exigirValido(req.cpf());
        if (contasCliente.existsByEmailIgnoreCase(email) || contasFuncionario.existsByEmailIgnoreCase(email)) {
            throw new NegocioException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        if (clientes.existsByCpf(cpf)) {
            throw new NegocioException(HttpStatus.CONFLICT, "CPF já cadastrado. Entre com o CPF ou com o e-mail da sua conta.");
        }
        ContaCliente conta = new ContaCliente();
        conta.setEmail(email);
        conta.setSenhaHash(encoder.encode(req.senha()));
        contasCliente.save(conta);

        Cliente cliente = new Cliente();
        cliente.setConta(conta);
        cliente.setNome(req.nome().trim());
        cliente.setCpf(cpf);
        try {
            clientes.saveAndFlush(cliente); // a chave única do banco cobre cadastros simultâneos com o mesmo CPF
        } catch (DataIntegrityViolationException e) {
            throw new NegocioException(HttpStatus.CONFLICT, "CPF já cadastrado. Entre com o CPF ou com o e-mail da sua conta.");
        }
        return tokenCliente(cliente);
    }

    /** Entra com e-mail ou CPF (único por conta) + senha. */
    @Transactional(readOnly = true)
    public TokenResponse loginCliente(LoginClienteRequest req, String ip) {
        String identificador = req.identificador();
        if (identificador.isEmpty()) {
            throw new NegocioException("Informe o e-mail ou o CPF");
        }
        boolean porEmail = identificador.contains("@");
        String cpf = porEmail ? null : Cpf.normalizar(identificador);
        String chave = "login:" + ip + ":" + (porEmail ? identificador.toLowerCase() : cpf);
        limiter.exigirDisponivel(chave);

        Optional<Cliente> cliente = porEmail
                ? contasCliente.findByEmailIgnoreCase(identificador).flatMap(c -> clientes.findByContaId(c.getId()))
                : Optional.of(cpf).filter(Cpf::valido).flatMap(clientes::findByCpf);
        String hash = cliente.map(c -> c.getConta().getSenhaHash()).orElse(null);
        if (!senhaConfere(req.senha(), hash) || !cliente.get().getConta().isAtiva()) {
            limiter.registrarFalha(chave);
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "E-mail/CPF ou senha inválidos");
        }
        limiter.limpar(chave);
        return tokenCliente(cliente.get());
    }

    @Transactional(readOnly = true)
    public TokenResponse loginFuncionario(LoginRequest req, String ip) {
        String chave = "login-func:" + ip + ":" + req.email().toLowerCase();
        limiter.exigirDisponivel(chave);
        Optional<ContaFuncionario> conta = contasFuncionario.findByEmailIgnoreCase(req.email().trim());
        if (!senhaConfere(req.senha(), conta.map(ContaFuncionario::getSenhaHash).orElse(null)) || !conta.get().isAtiva()) {
            limiter.registrarFalha(chave);
            throw credenciaisInvalidas();
        }
        limiter.limpar(chave);
        Funcionario f = funcionarios.findByContaId(conta.get().getId()).orElseThrow(AuthService::credenciaisInvalidas);
        return new TokenResponse(jwt.gerarTokenAutenticacao(f.getId(), TipoUsuario.FUNCIONARIO), "FUNCIONARIO",
                f.getNome(), jwt.expiracaoSegundos(), detalhes.permissoesDoFuncionario(f.getId()));
    }

    private boolean senhaConfere(String senha, String hash) {
        // sempre executa uma comparação BCrypt, exista ou não o usuário
        boolean ok = encoder.matches(senha, hash != null ? hash : hashFalso);
        return hash != null && ok;
    }

    private TokenResponse tokenCliente(Cliente c) {
        return new TokenResponse(jwt.gerarTokenAutenticacao(c.getId(), TipoUsuario.CLIENTE), "CLIENTE", c.getNome(),
                jwt.expiracaoSegundos(), List.of());
    }

    private static NegocioException credenciaisInvalidas() {
        return new NegocioException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
    }
}
