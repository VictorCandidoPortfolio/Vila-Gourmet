package com.vilagourmet.controller;

import com.vilagourmet.dto.ClienteDTO.ClienteResponse;
import com.vilagourmet.dto.ClienteDTO.EnderecoRequest;
import com.vilagourmet.dto.ClienteDTO.EnderecoResponse;
import com.vilagourmet.dto.ClienteDTO.PizzaSalvaRequest;
import com.vilagourmet.dto.ClienteDTO.PizzaSalvaResponse;
import com.vilagourmet.dto.MesaDTO.AcessoMesaResponse;
import com.vilagourmet.dto.MesaDTO.MesaPedidoRequest;
import com.vilagourmet.dto.PedidoDTO.CancelamentoResponse;
import com.vilagourmet.dto.PedidoDTO.NovoCodigoConfirmacaoResponse;
import com.vilagourmet.dto.PedidoDTO.PedidoCriadoResponse;
import com.vilagourmet.dto.PedidoDTO.PedidoResponse;
import com.vilagourmet.dto.PedidoDTO.RastreamentoResponse;
import com.vilagourmet.security.UsuarioAutenticado;
import com.vilagourmet.service.CancelamentoService;
import com.vilagourmet.service.ClienteService;
import com.vilagourmet.service.MesaService;
import com.vilagourmet.service.PedidoService;
import com.vilagourmet.service.RastreamentoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Área do cliente autenticado (papel CLIENTE). */
@RestController
@RequestMapping("/api/cliente")
public class ClienteController {

    private final ClienteService clientes;
    private final PedidoService pedidos;
    private final CancelamentoService cancelamento;
    private final RastreamentoService rastreamento;
    private final MesaService mesas;

    public ClienteController(ClienteService clientes, PedidoService pedidos, CancelamentoService cancelamento,
                             RastreamentoService rastreamento, MesaService mesas) {
        this.clientes = clientes;
        this.pedidos = pedidos;
        this.cancelamento = cancelamento;
        this.rastreamento = rastreamento;
        this.mesas = mesas;
    }

    @GetMapping("/me")
    public ClienteResponse perfil(@AuthenticationPrincipal UsuarioAutenticado u) {
        return clientes.perfil(u.id());
    }

    @GetMapping("/mesa")
    public ResponseEntity<AcessoMesaResponse> mesa(@AuthenticationPrincipal UsuarioAutenticado u) {
        return mesas.mesaDoCliente(u.id()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/mesa")
    @ResponseStatus(HttpStatus.CREATED)
    public AcessoMesaResponse abrirMesa(@AuthenticationPrincipal UsuarioAutenticado u) {
        return mesas.abrirParaCliente(u.id());
    }

    @PostMapping("/mesa/{codigo}/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoCriadoResponse pedidoMesa(@AuthenticationPrincipal UsuarioAutenticado u, @PathVariable String codigo,
                                         @Valid @RequestBody MesaPedidoRequest req,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
        return mesas.pedidoDoCliente(u.id(), codigo, req, chave);
    }

    @GetMapping("/enderecos")
    public List<EnderecoResponse> enderecos(@AuthenticationPrincipal UsuarioAutenticado u) {
        return clientes.listarEnderecos(u.id());
    }

    @PostMapping("/enderecos")
    @ResponseStatus(HttpStatus.CREATED)
    public EnderecoResponse criarEndereco(@AuthenticationPrincipal UsuarioAutenticado u, @Valid @RequestBody EnderecoRequest req) {
        return clientes.criarEndereco(u.id(), req);
    }

    @PutMapping("/enderecos/{id}")
    public EnderecoResponse atualizarEndereco(@AuthenticationPrincipal UsuarioAutenticado u, @PathVariable Long id,
                                              @Valid @RequestBody EnderecoRequest req) {
        return clientes.atualizarEndereco(u.id(), id, req);
    }

    @DeleteMapping("/enderecos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerEndereco(@AuthenticationPrincipal UsuarioAutenticado u, @PathVariable Long id) {
        clientes.removerEndereco(u.id(), id);
    }

    @GetMapping("/pizzas")
    public List<PizzaSalvaResponse> pizzas(@AuthenticationPrincipal UsuarioAutenticado u) {
        return clientes.listarPizzas(u.id());
    }

    @PostMapping("/pizzas")
    @ResponseStatus(HttpStatus.CREATED)
    public PizzaSalvaResponse salvarPizza(@AuthenticationPrincipal UsuarioAutenticado u, @Valid @RequestBody PizzaSalvaRequest req) {
        return clientes.salvarPizza(u.id(), req);
    }

    @DeleteMapping("/pizzas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerPizza(@AuthenticationPrincipal UsuarioAutenticado u, @PathVariable Long id) {
        clientes.removerPizza(u.id(), id);
    }

    @GetMapping("/pedidos")
    public List<PedidoResponse> historico(@AuthenticationPrincipal UsuarioAutenticado u) {
        return pedidos.historicoDoCliente(u.id());
    }

    @GetMapping("/pedidos/{id}/rastreamento")
    public RastreamentoResponse rastrear(@AuthenticationPrincipal UsuarioAutenticado u, @PathVariable Long id) {
        return rastreamento.consultarDoCliente(id, u.id());
    }

    @PostMapping("/pedidos/{id}/cancelamento")
    public CancelamentoResponse solicitarCancelamento(@AuthenticationPrincipal UsuarioAutenticado u, @PathVariable Long id) {
        return cancelamento.solicitarPorCliente(id, u.id());
    }

    @PostMapping("/pedidos/{id}/codigo-confirmacao")
    public NovoCodigoConfirmacaoResponse novoCodigo(@AuthenticationPrincipal UsuarioAutenticado u, @PathVariable Long id) {
        return clientes.novoCodigoConfirmacao(u.id(), id);
    }
}
