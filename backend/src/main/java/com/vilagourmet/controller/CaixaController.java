package com.vilagourmet.controller;

import com.vilagourmet.dto.CaixaDTO.AbrirMesaCaixaRequest;
import com.vilagourmet.dto.CaixaDTO.CaixaStatusResponse;
import com.vilagourmet.dto.CaixaDTO.FecharCaixaRequest;
import com.vilagourmet.dto.CaixaDTO.FechamentoCaixaResponse;
import com.vilagourmet.dto.CaixaDTO.PedidoCaixaRequest;
import com.vilagourmet.dto.MesaDTO.ContaMesaResponse;
import com.vilagourmet.dto.PedidoDTO.PedidoCriadoResponse;
import com.vilagourmet.security.UsuarioAutenticado;
import com.vilagourmet.service.CaixaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Frente de caixa: abrir/fechar o caixa e lançar pedidos de entrega, retirada e mesa. */
@RestController
@RequestMapping("/api/gestao/caixa")
@PreAuthorize("hasAuthority('CAIXA_OPERAR')")
public class CaixaController {

    private final CaixaService caixa;

    public CaixaController(CaixaService caixa) {
        this.caixa = caixa;
    }

    @GetMapping
    public CaixaStatusResponse status() {
        return caixa.status();
    }

    @PostMapping("/abrir")
    public CaixaStatusResponse abrir(@AuthenticationPrincipal UsuarioAutenticado u) {
        return caixa.abrir(u.id());
    }

    @PostMapping("/fechar")
    public FechamentoCaixaResponse fechar(@AuthenticationPrincipal UsuarioAutenticado u,
                                          @RequestBody(required = false) FecharCaixaRequest req) {
        return caixa.fechar(u.id(), req != null && Boolean.TRUE.equals(req.confirmarCancelamentos()));
    }

    @GetMapping("/mesas")
    public List<ContaMesaResponse> mesasAbertas() {
        return caixa.contasDeMesaAbertas();
    }

    @PostMapping("/mesas")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaMesaResponse abrirMesa(@Valid @RequestBody AbrirMesaCaixaRequest req) {
        return caixa.abrirContaDeMesa(req);
    }

    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoCriadoResponse lancarPedido(@Valid @RequestBody PedidoCaixaRequest req,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
        return caixa.lancarPedido(req, chave);
    }
}
