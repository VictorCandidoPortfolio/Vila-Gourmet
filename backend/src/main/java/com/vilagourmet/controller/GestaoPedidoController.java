package com.vilagourmet.controller;

import com.vilagourmet.dto.PagamentoDTO.ConfirmarPagamentoRequest;
import com.vilagourmet.dto.PagamentoDTO.PagamentoResponse;
import com.vilagourmet.dto.PedidoDTO.CancelamentoResponse;
import com.vilagourmet.dto.PedidoDTO.CancelarRequest;
import com.vilagourmet.dto.PedidoDTO.ConclusaoCodigoRequest;
import com.vilagourmet.dto.PedidoDTO.ConclusaoManualRequest;
import com.vilagourmet.dto.PedidoDTO.DecisaoCancelamentoRequest;
import com.vilagourmet.dto.PedidoDTO.PedidoResponse;
import com.vilagourmet.dto.PedidoDTO.TransicaoRequest;
import com.vilagourmet.model.enums.EstadoCancelamento;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.model.enums.Modalidade;
import com.vilagourmet.security.UsuarioAutenticado;
import com.vilagourmet.service.CancelamentoService;
import com.vilagourmet.service.PagamentoService;
import com.vilagourmet.service.PedidoEstadoService;
import com.vilagourmet.service.PedidoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Painel de pedidos, cancelamentos e pagamentos. */
@RestController
@RequestMapping("/api/gestao")
public class GestaoPedidoController {

    private final PedidoService pedidos;
    private final PedidoEstadoService estados;
    private final CancelamentoService cancelamento;
    private final PagamentoService pagamentos;

    public GestaoPedidoController(PedidoService pedidos, PedidoEstadoService estados, CancelamentoService cancelamento,
                                  PagamentoService pagamentos) {
        this.pedidos = pedidos;
        this.estados = estados;
        this.cancelamento = cancelamento;
        this.pagamentos = pagamentos;
    }

    @GetMapping("/pedidos")
    @PreAuthorize("hasAuthority('PEDIDO_VER')")
    public Page<PedidoResponse> listar(@RequestParam(required = false) EstadoPedido estado,
                                       @RequestParam(required = false) Modalidade modalidade,
                                       @RequestParam(required = false) PedidoService.Situacao situacao,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "20") int tamanho) {
        return pedidos.listar(estado, modalidade, situacao, de, ate, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 100)));
    }

    @GetMapping("/pedidos/{id}")
    @PreAuthorize("hasAuthority('PEDIDO_VER')")
    public PedidoResponse obter(@PathVariable Long id) {
        return pedidos.obter(id);
    }

    @PostMapping("/pedidos/{id}/estado")
    @PreAuthorize("hasAuthority('PEDIDO_GERENCIAR')")
    public PedidoResponse transicionar(@PathVariable Long id, @Valid @RequestBody TransicaoRequest req,
                                       @AuthenticationPrincipal UsuarioAutenticado u) {
        return estados.transicionar(id, req.estado(), u.id());
    }

    @PostMapping("/pedidos/{id}/concluir-com-codigo")
    @PreAuthorize("hasAuthority('PEDIDO_GERENCIAR')")
    public PedidoResponse concluirComCodigo(@PathVariable Long id, @Valid @RequestBody ConclusaoCodigoRequest req,
                                            @AuthenticationPrincipal UsuarioAutenticado u) {
        return estados.concluirComCodigo(id, req.codigo(), u.id());
    }

    @PostMapping("/pedidos/{id}/concluir-manual")
    @PreAuthorize("hasAuthority('PEDIDO_GERENCIAR')")
    public PedidoResponse concluirManual(@PathVariable Long id, @Valid @RequestBody ConclusaoManualRequest req,
                                         @AuthenticationPrincipal UsuarioAutenticado u) {
        return estados.concluirManual(id, req.justificativa(), u.id());
    }

    @PostMapping("/pedidos/{id}/cancelar")
    @PreAuthorize("hasAuthority('CANCELAMENTO_DECIDIR')")
    public CancelamentoResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarRequest req,
                                         @AuthenticationPrincipal UsuarioAutenticado u) {
        return cancelamento.solicitarPorFuncionario(id, u.id(), req.justificativa());
    }

    @PostMapping("/pedidos/{id}/pagamento")
    @PreAuthorize("hasAuthority('PAGAMENTO_CONFIRMAR')")
    public PagamentoResponse confirmarPagamento(@PathVariable Long id, @Valid @RequestBody ConfirmarPagamentoRequest req,
                                                @AuthenticationPrincipal UsuarioAutenticado u) {
        return pagamentos.confirmarPedido(id, req, u.id());
    }

    @GetMapping("/cancelamentos")
    @PreAuthorize("hasAuthority('CANCELAMENTO_DECIDIR')")
    public List<CancelamentoResponse> cancelamentos(@RequestParam(required = false) EstadoCancelamento estado) {
        return cancelamento.listar(estado);
    }

    @PostMapping("/cancelamentos/{id}/decisao")
    @PreAuthorize("hasAuthority('CANCELAMENTO_DECIDIR')")
    public CancelamentoResponse decidir(@PathVariable Long id, @Valid @RequestBody DecisaoCancelamentoRequest req,
                                        @AuthenticationPrincipal UsuarioAutenticado u) {
        return cancelamento.decidir(id, req.aprovar(), req.justificativa(), u.id());
    }
}
