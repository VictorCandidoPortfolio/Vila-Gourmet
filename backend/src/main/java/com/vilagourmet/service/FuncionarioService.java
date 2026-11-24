package com.vilagourmet.service;

import com.vilagourmet.dto.GestaoDTO.FuncionarioRequest;
import com.vilagourmet.dto.GestaoDTO.FuncionarioResponse;
import com.vilagourmet.dto.GestaoDTO.PerfilRequest;
import com.vilagourmet.dto.GestaoDTO.PerfilResponse;
import com.vilagourmet.dto.GestaoDTO.PermissaoResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.ContaFuncionario;
import com.vilagourmet.model.Funcionario;
import com.vilagourmet.model.Perfil;
import com.vilagourmet.model.Permissao;
import com.vilagourmet.repository.ContaClienteRepository;
import com.vilagourmet.repository.ContaFuncionarioRepository;
import com.vilagourmet.repository.FuncionarioRepository;
import com.vilagourmet.repository.PerfilRepository;
import com.vilagourmet.repository.PermissaoRepository;
import com.vilagourmet.util.SenhaSegura;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Funcionários, perfis e permissões. */
@Service
public class FuncionarioService {

    private static final String PERFIL_ADMIN = "ADMINISTRADOR";

    private final FuncionarioRepository funcionarios;
    private final ContaFuncionarioRepository contas;
    private final ContaClienteRepository contasCliente;
    private final PerfilRepository perfis;
    private final PermissaoRepository permissoes;
    private final PasswordEncoder encoder;

    public FuncionarioService(FuncionarioRepository funcionarios, ContaFuncionarioRepository contas,
                              ContaClienteRepository contasCliente, PerfilRepository perfis,
                              PermissaoRepository permissoes, PasswordEncoder encoder) {
        this.funcionarios = funcionarios;
        this.contas = contas;
        this.contasCliente = contasCliente;
        this.perfis = perfis;
        this.permissoes = permissoes;
        this.encoder = encoder;
    }

    // ---------------------------------------------------------------- funcionários

    @Transactional(readOnly = true)
    public List<FuncionarioResponse> listar() {
        return funcionarios.findAllByOrderByNomeAsc().stream().map(this::paraResponse).toList();
    }

    @Transactional
    public FuncionarioResponse criar(FuncionarioRequest req) {
        if (req.senha() == null || req.senha().isBlank()) {
            throw new NegocioException("A senha é obrigatória na criação do funcionário");
        }
        SenhaSegura.exigirValida(req.senha());
        String email = req.email().trim().toLowerCase();
        if (contas.existsByEmailIgnoreCase(email) || contasCliente.existsByEmailIgnoreCase(email)) {
            throw new NegocioException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        ContaFuncionario conta = new ContaFuncionario();
        conta.setEmail(email);
        conta.setSenhaHash(encoder.encode(req.senha()));
        conta.setAtiva(req.ativa() == null || req.ativa());
        contas.save(conta);
        Funcionario f = new Funcionario();
        f.setConta(conta);
        f.setNome(req.nome().trim());
        f.setPerfis(carregarPerfis(req.perfilIds()));
        return paraResponse(funcionarios.save(f));
    }

    @Transactional
    public FuncionarioResponse atualizar(Long id, FuncionarioRequest req, Long solicitanteId) {
        Funcionario f = funcionarios.findById(id).orElseThrow(() -> new NaoEncontradoException("Funcionário não encontrado"));
        String email = req.email().trim().toLowerCase();
        if (!f.getConta().getEmail().equalsIgnoreCase(email)
                && (contas.existsByEmailIgnoreCase(email) || contasCliente.existsByEmailIgnoreCase(email))) {
            throw new NegocioException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        if (req.ativa() != null && !req.ativa() && id.equals(solicitanteId)) {
            throw new NegocioException("Você não pode desativar a própria conta");
        }
        f.setNome(req.nome().trim());
        f.getConta().setEmail(email);
        if (req.senha() != null && !req.senha().isBlank()) {
            SenhaSegura.exigirValida(req.senha());
            f.getConta().setSenhaHash(encoder.encode(req.senha()));
        }
        if (req.ativa() != null) {
            f.getConta().setAtiva(req.ativa());
        }
        f.setPerfis(carregarPerfis(req.perfilIds()));
        return paraResponse(f);
    }

    @Transactional(readOnly = true)
    public boolean semFuncionarios() {
        return funcionarios.count() == 0;
    }

    /** Cria o administrador inicial se não existir nenhum funcionário. Retorna true se criou. */
    @Transactional
    public boolean criarAdministradorInicialSeNecessario(String email, String senha, String nome) {
        SenhaSegura.exigirValida(senha);
        if (funcionarios.count() > 0) {
            return false;
        }
        Perfil admin = perfis.findAllByOrderByNomeAsc().stream().filter(p -> PERFIL_ADMIN.equals(p.getNome())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Perfil ADMINISTRADOR não encontrado (migração V2)"));
        ContaFuncionario conta = new ContaFuncionario();
        conta.setEmail(email.trim().toLowerCase());
        conta.setSenhaHash(encoder.encode(senha));
        contas.save(conta);
        Funcionario f = new Funcionario();
        f.setConta(conta);
        f.setNome(nome);
        f.getPerfis().add(admin);
        funcionarios.save(f);
        return true;
    }

    // ---------------------------------------------------------------- perfis e permissões

    @Transactional(readOnly = true)
    public List<PermissaoResponse> listarPermissoes() {
        return permissoes.findAllByOrderByCodigoAsc().stream()
                .map(p -> new PermissaoResponse(p.getId(), p.getCodigo(), p.getDescricao())).toList();
    }

    @Transactional(readOnly = true)
    public List<PerfilResponse> listarPerfis() {
        return perfis.findAllByOrderByNomeAsc().stream().map(this::paraResponse).toList();
    }

    @Transactional
    public PerfilResponse criarPerfil(PerfilRequest req) {
        if (perfis.existsByNomeIgnoreCase(req.nome().trim())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Já existe um perfil com esse nome");
        }
        Perfil p = new Perfil();
        preencher(p, req);
        return paraResponse(perfis.save(p));
    }

    @Transactional
    public PerfilResponse atualizarPerfil(Long id, PerfilRequest req) {
        Perfil p = perfis.findById(id).orElseThrow(() -> new NaoEncontradoException("Perfil não encontrado"));
        if (!p.getNome().equalsIgnoreCase(req.nome().trim()) && perfis.existsByNomeIgnoreCase(req.nome().trim())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Já existe um perfil com esse nome");
        }
        if (PERFIL_ADMIN.equals(p.getNome()) && (req.ativo() != null && !req.ativo())) {
            throw new NegocioException("O perfil ADMINISTRADOR não pode ser desativado");
        }
        preencher(p, req);
        return paraResponse(p);
    }

    private void preencher(Perfil p, PerfilRequest req) {
        p.setNome(req.nome().trim());
        p.setDescricao(req.descricao());
        if (req.ativo() != null) {
            p.setAtivo(req.ativo());
        }
        List<Permissao> encontradas = permissoes.findAllById(req.permissaoIds());
        if (encontradas.size() != req.permissaoIds().size()) {
            throw new NaoEncontradoException("Permissão não encontrada");
        }
        p.setPermissoes(new HashSet<>(encontradas));
    }

    private Set<Perfil> carregarPerfis(Set<Long> ids) {
        List<Perfil> encontrados = perfis.findAllById(ids);
        if (encontrados.size() != ids.size()) {
            throw new NaoEncontradoException("Perfil não encontrado");
        }
        return new HashSet<>(encontrados);
    }

    private FuncionarioResponse paraResponse(Funcionario f) {
        return new FuncionarioResponse(f.getId(), f.getNome(), f.getConta().getEmail(), f.getConta().isAtiva(),
                f.getPerfis().stream().map(Perfil::getNome).sorted().toList());
    }

    private PerfilResponse paraResponse(Perfil p) {
        return new PerfilResponse(p.getId(), p.getNome(), p.getDescricao(), p.isAtivo(),
                p.getPermissoes().stream().sorted(Comparator.comparing(Permissao::getCodigo))
                        .map(x -> new PermissaoResponse(x.getId(), x.getCodigo(), x.getDescricao())).toList());
    }
}
