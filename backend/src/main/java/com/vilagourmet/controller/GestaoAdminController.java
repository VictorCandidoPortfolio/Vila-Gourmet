package com.vilagourmet.controller;

import com.vilagourmet.dto.AuthDTO.SessaoFuncionarioResponse;
import com.vilagourmet.dto.GestaoDTO.AreaEntregaRequest;
import com.vilagourmet.dto.GestaoDTO.AreaEntregaResponse;
import com.vilagourmet.dto.GestaoDTO.FuncionarioRequest;
import com.vilagourmet.dto.GestaoDTO.ImportacaoCidadeResponse;
import com.vilagourmet.dto.GestaoDTO.ImportarCidadeRequest;
import com.vilagourmet.dto.GestaoDTO.MunicipioResponse;
import com.vilagourmet.dto.GestaoDTO.FuncionarioResponse;
import com.vilagourmet.dto.GestaoDTO.PerfilRequest;
import com.vilagourmet.dto.GestaoDTO.PerfilResponse;
import com.vilagourmet.dto.GestaoDTO.PermissaoResponse;
import com.vilagourmet.dto.GestaoDTO.TaxaEntregaRequest;
import com.vilagourmet.dto.GestaoDTO.TaxaEntregaResponse;
import com.vilagourmet.dto.RelatorioDTO.RelatorioOperacionalResponse;
import com.vilagourmet.security.UsuarioAutenticado;
import com.vilagourmet.service.CidadeEntregaService;
import com.vilagourmet.service.ConfiguracaoService;
import com.vilagourmet.service.FuncionarioService;
import com.vilagourmet.service.UsuarioDetalhesService;
import com.vilagourmet.repository.FuncionarioRepository;
import com.vilagourmet.service.RelatorioService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Configuração da loja, funcionários, perfis, permissões e relatórios. */
@RestController
@RequestMapping("/api/gestao")
public class GestaoAdminController {

    private final ConfiguracaoService configuracao;
    private final FuncionarioService funcionarios;
    private final RelatorioService relatorios;
    private final CidadeEntregaService cidades;
    private final UsuarioDetalhesService detalhes;
    private final FuncionarioRepository funcionariosRepo;

    public GestaoAdminController(ConfiguracaoService configuracao, FuncionarioService funcionarios, RelatorioService relatorios,
                                 CidadeEntregaService cidades, UsuarioDetalhesService detalhes,
                                 FuncionarioRepository funcionariosRepo) {
        this.configuracao = configuracao;
        this.funcionarios = funcionarios;
        this.relatorios = relatorios;
        this.cidades = cidades;
        this.detalhes = detalhes;
        this.funcionariosRepo = funcionariosRepo;
    }

    /** Nome e permissões atuais do funcionário logado. */
    @GetMapping("/eu")
    public SessaoFuncionarioResponse eu(@AuthenticationPrincipal UsuarioAutenticado u) {
        String nome = funcionariosRepo.findById(u.id()).map(f -> f.getNome()).orElse("");
        return new SessaoFuncionarioResponse(nome, detalhes.permissoesDoFuncionario(u.id()));
    }

    // ---------------------------------------------------------------- entrega

    @GetMapping("/entrega/taxa")
    @PreAuthorize("hasAuthority('CONFIG_GERENCIAR')")
    public TaxaEntregaResponse taxa() {
        return new TaxaEntregaResponse(configuracao.taxaEntrega());
    }

    @PutMapping("/entrega/taxa")
    @PreAuthorize("hasAuthority('CONFIG_GERENCIAR')")
    public TaxaEntregaResponse definirTaxa(@Valid @RequestBody TaxaEntregaRequest req) {
        return configuracao.definirTaxa(req.taxaEntrega());
    }

    @GetMapping("/entrega/areas")
    @PreAuthorize("hasAuthority('CONFIG_GERENCIAR')")
    public List<AreaEntregaResponse> areas() {
        return configuracao.listarAreas();
    }

    @PostMapping("/entrega/areas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONFIG_GERENCIAR')")
    public AreaEntregaResponse criarArea(@Valid @RequestBody AreaEntregaRequest req) {
        return configuracao.criarArea(req);
    }

    @PutMapping("/entrega/areas/{id}")
    @PreAuthorize("hasAuthority('CONFIG_GERENCIAR')")
    public AreaEntregaResponse atualizarArea(@PathVariable Long id, @Valid @RequestBody AreaEntregaRequest req) {
        return configuracao.atualizarArea(id, req);
    }

    /** Sugestões de municípios (IBGE) para importar a área de entrega. */
    @GetMapping("/entrega/municipios")
    @PreAuthorize("hasAuthority('CONFIG_GERENCIAR')")
    public List<MunicipioResponse> municipios(@RequestParam String busca) {
        return cidades.pesquisar(busca);
    }

    /** Importa os bairros do município (OpenStreetMap). Pode levar cerca de um minuto. */
    @PostMapping("/entrega/cidades/importar")
    @PreAuthorize("hasAuthority('CONFIG_GERENCIAR')")
    public ImportacaoCidadeResponse importarCidade(@Valid @RequestBody ImportarCidadeRequest req) {
        return cidades.importar(req.ibgeId());
    }

    // ---------------------------------------------------------------- funcionários, perfis e permissões

    @GetMapping("/funcionarios")
    @PreAuthorize("hasAuthority('FUNCIONARIO_GERENCIAR')")
    public List<FuncionarioResponse> funcionarios() {
        return funcionarios.listar();
    }

    @PostMapping("/funcionarios")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FUNCIONARIO_GERENCIAR')")
    public FuncionarioResponse criarFuncionario(@Valid @RequestBody FuncionarioRequest req) {
        return funcionarios.criar(req);
    }

    @PutMapping("/funcionarios/{id}")
    @PreAuthorize("hasAuthority('FUNCIONARIO_GERENCIAR')")
    public FuncionarioResponse atualizarFuncionario(@PathVariable Long id, @Valid @RequestBody FuncionarioRequest req,
                                                    @AuthenticationPrincipal UsuarioAutenticado u) {
        return funcionarios.atualizar(id, req, u.id());
    }

    @GetMapping("/perfis")
    @PreAuthorize("hasAnyAuthority('PERFIL_GERENCIAR', 'FUNCIONARIO_GERENCIAR')")
    public List<PerfilResponse> perfis() {
        return funcionarios.listarPerfis();
    }

    @PostMapping("/perfis")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERFIL_GERENCIAR')")
    public PerfilResponse criarPerfil(@Valid @RequestBody PerfilRequest req) {
        return funcionarios.criarPerfil(req);
    }

    @PutMapping("/perfis/{id}")
    @PreAuthorize("hasAuthority('PERFIL_GERENCIAR')")
    public PerfilResponse atualizarPerfil(@PathVariable Long id, @Valid @RequestBody PerfilRequest req) {
        return funcionarios.atualizarPerfil(id, req);
    }

    @GetMapping("/permissoes")
    @PreAuthorize("hasAuthority('PERFIL_GERENCIAR')")
    public List<PermissaoResponse> permissoes() {
        return funcionarios.listarPermissoes();
    }

    // ---------------------------------------------------------------- relatórios

    @GetMapping("/relatorios/operacional")
    @PreAuthorize("hasAuthority('RELATORIO_VER')")
    public RelatorioOperacionalResponse operacional(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return relatorios.operacional(inicio, fim);
    }
}
