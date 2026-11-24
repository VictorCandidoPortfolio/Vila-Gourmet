package com.vilagourmet.controller;

import com.vilagourmet.dto.CatalogoDTO.ProdutoResponse;
import com.vilagourmet.dto.CaixaDTO.LojaStatusResponse;
import com.vilagourmet.dto.ClienteDTO.CepResponse;
import com.vilagourmet.dto.CotacaoDTO.CotacaoRequest;
import com.vilagourmet.dto.CotacaoDTO.CotacaoResponse;
import com.vilagourmet.dto.GestaoDTO.AreaConsultaResponse;
import com.vilagourmet.dto.MesaDTO.AbrirMesaRequest;
import com.vilagourmet.dto.MesaDTO.AcessarMesaRequest;
import com.vilagourmet.dto.MesaDTO.AcessoMesaResponse;
import com.vilagourmet.dto.MesaDTO.ContaMesaResponse;
import com.vilagourmet.dto.MesaDTO.MesaPedidoRequest;
import com.vilagourmet.dto.PedidoDTO.CancelamentoResponse;
import com.vilagourmet.dto.PedidoDTO.PedidoCriadoResponse;
import com.vilagourmet.dto.PedidoDTO.PedidoRequest;
import com.vilagourmet.dto.PedidoDTO.RastreamentoRequest;
import com.vilagourmet.dto.PedidoDTO.RastreamentoResponse;
import com.vilagourmet.security.UsuarioAutenticado;
import com.vilagourmet.service.CancelamentoService;
import com.vilagourmet.service.AberturaLojaService;
import com.vilagourmet.service.CatalogoService;
import com.vilagourmet.service.CepService;
import com.vilagourmet.service.ConfiguracaoService;
import com.vilagourmet.service.CotacaoService;
import com.vilagourmet.service.MesaService;
import com.vilagourmet.service.PedidoService;
import com.vilagourmet.service.RastreamentoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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

/** Endpoints abertos a visitantes: catálogo, cotação, pedidos de retirada/entrega, rastreamento e mesas. */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final CatalogoService catalogo;
    private final CotacaoService cotacao;
    private final ConfiguracaoService configuracao;
    private final PedidoService pedidos;
    private final RastreamentoService rastreamento;
    private final CancelamentoService cancelamento;
    private final MesaService mesas;
    private final CepService ceps;
    private final AberturaLojaService abertura;

    public PublicController(CatalogoService catalogo, CotacaoService cotacao, ConfiguracaoService configuracao,
                            PedidoService pedidos, RastreamentoService rastreamento, CancelamentoService cancelamento,
                            MesaService mesas, CepService ceps, AberturaLojaService abertura) {
        this.catalogo = catalogo;
        this.cotacao = cotacao;
        this.configuracao = configuracao;
        this.pedidos = pedidos;
        this.rastreamento = rastreamento;
        this.cancelamento = cancelamento;
        this.mesas = mesas;
        this.ceps = ceps;
        this.abertura = abertura;
    }

    /** A loja só vende quando o caixa está aberto e a sessão liberou vendas online. */
    @GetMapping("/loja/status")
    public LojaStatusResponse statusDaLoja() {
        return abertura.caixaAberto().filter(c -> c.isVendasOnline())
                .map(c -> new LojaStatusResponse(true, c.getAbertoEm()))
                .orElse(new LojaStatusResponse(false, null));
    }

    @GetMapping("/catalogo")
    public List<ProdutoResponse> catalogo() {
        return catalogo.catalogoPublico();
    }

    /** Endereço de um CEP (ViaCEP), usado para preencher o formulário de endereço. */
    @GetMapping("/cep/{cep}")
    public CepResponse consultarCep(@PathVariable String cep) {
        return ceps.consultar(cep);
    }

    @PostMapping("/cotacoes")
    public CotacaoResponse cotar(@Valid @RequestBody CotacaoRequest req) {
        return cotacao.cotar(req);
    }

    @GetMapping("/entrega/area")
    public AreaConsultaResponse consultarArea(@RequestParam String bairro, @RequestParam String cidade,
                                              @RequestParam(required = false) String uf) {
        return configuracao.consultar(bairro, cidade, uf);
    }

    /** ENTREGA exige cliente autenticado; RETIRADA aceita visitante. */
    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoCriadoResponse criarPedido(@Valid @RequestBody PedidoRequest req,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
                                            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return pedidos.criar(req, usuario, chave);
    }

    @PostMapping("/rastreamento")
    public RastreamentoResponse rastrear(@RequestBody RastreamentoRequest req, HttpServletRequest http) {
        return rastreamento.consultar(req, http.getRemoteAddr());
    }

    @PostMapping("/pedidos/cancelamento")
    public CancelamentoResponse solicitarCancelamento(@RequestBody RastreamentoRequest req, HttpServletRequest http) {
        return cancelamento.solicitarPublico(req, http.getRemoteAddr());
    }

    @PostMapping("/mesas")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaMesaResponse abrirMesa(@Valid @RequestBody AbrirMesaRequest req) {
        return mesas.abrir(req);
    }

    @PostMapping("/mesas/acesso")
    public AcessoMesaResponse acessarMesa(@Valid @RequestBody AcessarMesaRequest req, HttpServletRequest http) {
        return mesas.acessarPorCpf(req.cpf(), http.getRemoteAddr());
    }

    @PostMapping("/mesas/{codigo}/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoCriadoResponse pedidoDeMesa(@PathVariable String codigo, @Valid @RequestBody MesaPedidoRequest req,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String chave,
                                             HttpServletRequest http) {
        return mesas.pedidoDoResponsavel(codigo, req, http.getRemoteAddr(), chave);
    }
}
