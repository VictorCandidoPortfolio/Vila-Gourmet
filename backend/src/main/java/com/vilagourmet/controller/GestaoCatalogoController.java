package com.vilagourmet.controller;

import com.vilagourmet.dto.CatalogoDTO.*;
import com.vilagourmet.service.CatalogoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administração do catálogo (permissão CATALOGO_GERENCIAR). */
@RestController
@RequestMapping("/api/gestao")
@PreAuthorize("hasAuthority('CATALOGO_GERENCIAR')")
public class GestaoCatalogoController {

    private final CatalogoService catalogo;

    public GestaoCatalogoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return catalogo.listarCategorias();
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse criarCategoria(@Valid @RequestBody CategoriaRequest req) {
        return catalogo.criarCategoria(req);
    }

    @PutMapping("/categorias/{id}")
    public CategoriaResponse atualizarCategoria(@PathVariable Long id, @Valid @RequestBody CategoriaRequest req) {
        return catalogo.atualizarCategoria(id, req);
    }

    @DeleteMapping("/categorias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirCategoria(@PathVariable Long id) {
        catalogo.excluirCategoria(id);
    }

    @DeleteMapping("/insumos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirInsumo(@PathVariable Long id) {
        catalogo.excluirInsumo(id);
    }

    @GetMapping("/insumos")
    public List<InsumoResponse> insumos() {
        return catalogo.listarInsumos();
    }

    @PostMapping("/insumos")
    @ResponseStatus(HttpStatus.CREATED)
    public InsumoResponse criarInsumo(@Valid @RequestBody InsumoRequest req) {
        return catalogo.criarInsumo(req);
    }

    @PutMapping("/insumos/{id}")
    public InsumoResponse atualizarInsumo(@PathVariable Long id, @Valid @RequestBody InsumoRequest req) {
        return catalogo.atualizarInsumo(id, req);
    }

    @GetMapping("/produtos")
    public List<ProdutoResponse> produtos() {
        return catalogo.listarProdutos();
    }

    @GetMapping("/produtos/{id}")
    public ProdutoResponse produto(@PathVariable Long id) {
        return catalogo.obterProduto(id);
    }

    @PostMapping("/produtos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResponse criarProduto(@Valid @RequestBody ProdutoRequest req) {
        return catalogo.criarProduto(req);
    }

    @PutMapping("/produtos/{id}")
    public ProdutoResponse atualizarProduto(@PathVariable Long id, @Valid @RequestBody ProdutoRequest req) {
        return catalogo.atualizarProduto(id, req);
    }

    @GetMapping("/produtos/{id}/validacao")
    public ValidacaoProdutoResponse validar(@PathVariable Long id) {
        return catalogo.validarProduto(id);
    }

    @PostMapping("/produtos/{id}/publicar")
    public ProdutoResponse publicar(@PathVariable Long id) {
        return catalogo.publicar(id);
    }

    @PostMapping("/produtos/{id}/disponibilidade")
    public ProdutoResponse disponibilidade(@PathVariable Long id, @Valid @RequestBody DisponibilidadeRequest req) {
        return catalogo.alterarDisponibilidade(id, req.disponivel());
    }

    @DeleteMapping("/produtos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirProduto(@PathVariable Long id) {
        catalogo.excluirProduto(id);
    }

    @PostMapping("/produtos/{id}/despublicar")
    public ProdutoResponse despublicar(@PathVariable Long id) {
        return catalogo.despublicar(id);
    }

    @PostMapping("/produtos/{id}/grupos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResponse criarGrupo(@PathVariable Long id, @Valid @RequestBody GrupoRequest req) {
        return catalogo.criarGrupo(id, req);
    }

    @PutMapping("/grupos/{id}")
    public ProdutoResponse atualizarGrupo(@PathVariable Long id, @Valid @RequestBody GrupoRequest req) {
        return catalogo.atualizarGrupo(id, req);
    }

    @PostMapping("/grupos/{id}/opcoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResponse criarOpcao(@PathVariable Long id, @Valid @RequestBody OpcaoRequest req) {
        return catalogo.criarOpcao(id, req);
    }

    @PutMapping("/opcoes/{id}")
    public ProdutoResponse atualizarOpcao(@PathVariable Long id, @Valid @RequestBody OpcaoRequest req) {
        return catalogo.atualizarOpcao(id, req);
    }

    @PostMapping("/produtos/{id}/adicionais")
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResponse criarAdicional(@PathVariable Long id, @Valid @RequestBody AdicionalRequest req) {
        return catalogo.criarAdicional(id, req);
    }

    @PutMapping("/adicionais/{id}")
    public ProdutoResponse atualizarAdicional(@PathVariable Long id, @Valid @RequestBody AdicionalRequest req) {
        return catalogo.atualizarAdicional(id, req);
    }
}
