package com.vilagourmet.controller;

import com.vilagourmet.dto.MesaDTO.ContaMesaResponse;
import com.vilagourmet.dto.MesaDTO.MesaPedidoRequest;
import com.vilagourmet.dto.PagamentoDTO.ConfirmarPagamentoRequest;
import com.vilagourmet.dto.PagamentoDTO.PagamentoResponse;
import com.vilagourmet.dto.PedidoDTO.CancelarRequest;
import com.vilagourmet.dto.PedidoDTO.PedidoCriadoResponse;
import com.vilagourmet.model.enums.EstadoContaMesa;
import com.vilagourmet.security.UsuarioAutenticado;
import com.vilagourmet.service.MesaService;
import com.vilagourmet.service.PagamentoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Contas de mesa: localização pelo código, pedidos, fechamento e pagamento. */
@RestController
@RequestMapping("/api/gestao/mesas")
@PreAuthorize("hasAuthority('MESA_GERENCIAR')")
public class GestaoMesaController {

    private final MesaService mesas;
    private final PagamentoService pagamentos;

    public GestaoMesaController(MesaService mesas, PagamentoService pagamentos) {
        this.mesas = mesas;
        this.pagamentos = pagamentos;
    }

    @GetMapping
    public List<ContaMesaResponse> listar(@RequestParam(required = false) EstadoContaMesa estado) {
        return mesas.listar(estado);
    }

    @GetMapping("/{codigo}")
    public ContaMesaResponse obter(@PathVariable String codigo) {
        return mesas.obter(codigo);
    }

    @PostMapping("/{codigo}/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoCriadoResponse adicionarPedido(@PathVariable String codigo, @Valid @RequestBody MesaPedidoRequest req,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
        return mesas.pedidoPeloFuncionario(codigo, req, chave);
    }

    @PostMapping("/{codigo}/fechamento")
    public ContaMesaResponse iniciarFechamento(@PathVariable String codigo, @AuthenticationPrincipal UsuarioAutenticado u) {
        return mesas.iniciarFechamento(codigo, u.id());
    }

    @PostMapping("/{codigo}/fechamento/cancelar")
    public ContaMesaResponse cancelarFechamento(@PathVariable String codigo, @Valid @RequestBody CancelarRequest req,
                                                @AuthenticationPrincipal UsuarioAutenticado u) {
        return mesas.cancelarFechamento(codigo, u.id(), req.justificativa());
    }

    @PostMapping("/fechamentos/{id}/pagamento")
    @PreAuthorize("hasAuthority('PAGAMENTO_CONFIRMAR')")
    public PagamentoResponse pagarFechamento(@PathVariable Long id, @Valid @RequestBody ConfirmarPagamentoRequest req,
                                             @AuthenticationPrincipal UsuarioAutenticado u) {
        return pagamentos.confirmarFechamento(id, req, u.id());
    }
}
