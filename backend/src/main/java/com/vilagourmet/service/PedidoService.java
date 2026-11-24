package com.vilagourmet.service;

import com.vilagourmet.dto.CotacaoDTO.ItemCotado;
import com.vilagourmet.dto.CotacaoDTO.LinhaAdicional;
import com.vilagourmet.dto.CotacaoDTO.LinhaEscolha;
import com.vilagourmet.dto.CaixaDTO.PedidoCaixaRequest;
import com.vilagourmet.dto.PedidoDTO.ItemRequest;
import com.vilagourmet.dto.PedidoDTO.PedidoCriadoResponse;
import com.vilagourmet.dto.PedidoDTO.PedidoRequest;
import com.vilagourmet.dto.PedidoDTO.PedidoResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.AdicionalItemPedido;
import com.vilagourmet.model.Cliente;
import com.vilagourmet.model.ContaMesa;
import com.vilagourmet.model.Endereco;
import com.vilagourmet.model.EscolhaItemPedido;
import com.vilagourmet.model.ItemPedido;
import com.vilagourmet.model.Pagamento;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.enums.EstadoContaMesa;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.model.enums.Modalidade;
import com.vilagourmet.repository.ClienteRepository;
import com.vilagourmet.repository.EnderecoRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import com.vilagourmet.security.UsuarioAutenticado;
import com.vilagourmet.util.Cpf;
import com.vilagourmet.util.Dinheiro;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação e confirmação de pedidos. Totais, preços e snapshots são sempre calculados pelo servidor
 * dentro de uma única transação (pedido + itens + histórico + códigos).
 */
@Service
public class PedidoService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();
    private final PedidoRepository pedidos;
    private final ClienteRepository clientes;
    private final EnderecoRepository enderecos;
    private final PagamentoRepository pagamentos;
    private final CotacaoService cotacao;
    private final ConfiguracaoService configuracao;
    private final CodigoService codigos;
    private final PedidoEstadoService estados;
    private final PedidoMapper mapper;
    private final AberturaLojaService abertura;

    public PedidoService(PedidoRepository pedidos, ClienteRepository clientes, EnderecoRepository enderecos,
                         PagamentoRepository pagamentos, CotacaoService cotacao, ConfiguracaoService configuracao,
                         CodigoService codigos, PedidoEstadoService estados, PedidoMapper mapper,
                         AberturaLojaService abertura) {
        this.pedidos = pedidos;
        this.clientes = clientes;
        this.enderecos = enderecos;
        this.pagamentos = pagamentos;
        this.cotacao = cotacao;
        this.configuracao = configuracao;
        this.codigos = codigos;
        this.estados = estados;
        this.mapper = mapper;
        this.abertura = abertura;
    }

    /** Pedido de ENTREGA (cliente autenticado) ou RETIRADA (visitante ou cliente). */
    @Transactional
    public PedidoCriadoResponse criar(PedidoRequest req, UsuarioAutenticado usuario, String chaveIdempotencia) {
        abertura.exigirVendasOnline();
        if (req.modalidade() == Modalidade.MESA) {
            throw new NegocioException("Pedidos de mesa são feitos na conta de mesa");
        }
        String chave = normalizarChave(chaveIdempotencia);
        if (chave != null) {
            var existente = pedidos.findByChaveIdempotencia(chave);
            if (existente.isPresent()) {
                return new PedidoCriadoResponse(mapper.toResponse(existente.get(), true), null, null);
            }
        }

        Cliente cliente = usuario != null && usuario.ehCliente()
                ? clientes.findById(usuario.id()).orElseThrow(() -> new NaoEncontradoException("Cliente não encontrado"))
                : null;

        Pedido p = new Pedido();
        p.setChaveIdempotencia(chave);
        p.setModalidade(req.modalidade());
        p.setObservacao(req.observacao());
        BigDecimal taxa = BigDecimal.ZERO;

        if (req.modalidade() == Modalidade.ENTREGA) {
            if (cliente == null) {
                throw new NegocioException(HttpStatus.FORBIDDEN, "A entrega exige cliente cadastrado e autenticado");
            }
            if (req.enderecoId() == null) {
                throw new NegocioException("Informe o endereço de entrega");
            }
            Endereco e = enderecos.findByIdAndClienteIdAndAtivoTrue(req.enderecoId(), cliente.getId())
                    .orElseThrow(() -> new NegocioException("Endereço não encontrado entre os endereços salvos do cliente"));
            taxa = aplicarEntrega(p, cliente, e);
        } else {
            // retirada: nome e CPF são obrigatórios mesmo para quem tem conta
            if (req.nomeResponsavel() == null || req.nomeResponsavel().isBlank()) {
                throw new NegocioException("Nome é obrigatório para retirada");
            }
            p.setNomeResponsavel(req.nomeResponsavel().trim());
            p.setCpfResponsavel(Cpf.exigirValido(req.cpfResponsavel()));
            p.setClienteId(cliente == null ? null : cliente.getId());
        }

        BigDecimal subtotal = montarItens(p, req.itens());
        p.setSubtotal(subtotal);
        p.setTaxaEntrega(Dinheiro.arredondar(taxa));
        p.setTotal(Dinheiro.arredondar(subtotal.add(taxa)));
        persistir(p);

        String acompanhamento = codigos.gerarCodigoAcompanhamento(p);
        String confirmacao = codigos.gerarCodigoConfirmacao(p);
        if (req.metodoPagamento() != null) {
            Pagamento pg = new Pagamento();
            pg.setPedidoId(p.getId());
            pg.setMetodo(req.metodoPagamento());
            pg.setValor(p.getTotal());
            pagamentos.save(pg); // PENDENTE: apenas a preferência do cliente
        }
        return new PedidoCriadoResponse(mapper.toResponse(p, true), acompanhamento, confirmacao);
    }

    /**
     * Pedido lançado por um funcionário na frente de caixa (ENTREGA ou RETIRADA; mesa usa a conta de mesa).
     * Só o nome do cliente é obrigatório (sem conta de cliente; CPF opcional). A entrega usa o endereço digitado
     * e continua restrita à área atendida.
     */
    @Transactional
    public PedidoCriadoResponse criarPeloCaixa(PedidoCaixaRequest req, String chaveIdempotencia) {
        abertura.exigirCaixaAberto();
        if (req.modalidade() == Modalidade.MESA) {
            throw new NegocioException("Pedidos de mesa são lançados na conta de mesa");
        }
        String chave = normalizarChave(chaveIdempotencia);
        if (chave != null) {
            var existente = pedidos.findByChaveIdempotencia(chave);
            if (existente.isPresent()) {
                return new PedidoCriadoResponse(mapper.toResponse(existente.get(), true), null, null);
            }
        }
        if (req.nomeResponsavel() == null || req.nomeResponsavel().isBlank()) {
            throw new NegocioException("Informe o nome do cliente");
        }
        Pedido p = new Pedido();
        p.setChaveIdempotencia(chave);
        p.setModalidade(req.modalidade());
        p.setObservacao(req.observacao());
        p.setNomeResponsavel(req.nomeResponsavel().trim());
        p.setCpfResponsavel(req.cpfResponsavel() == null || req.cpfResponsavel().isBlank() ? null : Cpf.exigirValido(req.cpfResponsavel()));
        p.setClienteId(null);
        BigDecimal taxa = BigDecimal.ZERO;
        if (req.modalidade() == Modalidade.ENTREGA) {
            var e = req.endereco();
            if (e == null) {
                throw new NegocioException("Informe o endereço de entrega");
            }
            String cep = e.cep().replaceAll("\\D", "");
            if (cep.length() != 8) {
                throw new NegocioException("CEP inválido: informe os 8 dígitos");
            }
            if (!configuracao.areaAtendida(e.bairro(), e.cidade(), e.estado())) {
                throw new NegocioException("Endereço fora da área de entrega atendida");
            }
            p.setEnderecoCep(cep);
            p.setEnderecoLogradouro(e.logradouro().trim());
            p.setEnderecoNumero(e.numero().trim());
            p.setEnderecoComplemento(e.complemento() == null || e.complemento().isBlank() ? null : e.complemento().trim());
            p.setEnderecoBairro(e.bairro().trim());
            p.setEnderecoCidade(e.cidade().trim());
            p.setEnderecoEstado(e.estado().trim().toUpperCase());
            taxa = configuracao.taxaEntrega();
        }
        BigDecimal subtotal = montarItens(p, req.itens());
        p.setSubtotal(subtotal);
        p.setTaxaEntrega(Dinheiro.arredondar(taxa));
        p.setTotal(Dinheiro.arredondar(subtotal.add(taxa)));
        persistir(p);

        String acompanhamento = codigos.gerarCodigoAcompanhamento(p);
        String confirmacao = codigos.gerarCodigoConfirmacao(p);
        if (req.metodoPagamento() != null) {
            Pagamento pg = new Pagamento();
            pg.setPedidoId(p.getId());
            pg.setMetodo(req.metodoPagamento());
            pg.setValor(p.getTotal());
            pagamentos.save(pg);
        }
        return new PedidoCriadoResponse(mapper.toResponse(p, true), acompanhamento, confirmacao);
    }

    /** Confere a área atendida, grava o cliente e o endereço (snapshot) no pedido e devolve a taxa de entrega. */
    private BigDecimal aplicarEntrega(Pedido p, Cliente cliente, Endereco e) {
        if (!configuracao.areaAtendida(e.getBairro(), e.getCidade(), e.getEstado())) {
            throw new NegocioException("Endereço fora da área de entrega atendida");
        }
        p.setClienteId(cliente.getId());
        p.setNomeResponsavel(cliente.getNome());
        p.setCpfResponsavel(cliente.getCpf());
        p.setEnderecoCep(e.getCep());
        p.setEnderecoLogradouro(e.getLogradouro());
        p.setEnderecoNumero(e.getNumero());
        p.setEnderecoComplemento(e.getComplemento());
        p.setEnderecoBairro(e.getBairro());
        p.setEnderecoCidade(e.getCidade());
        p.setEnderecoEstado(e.getEstado());
        return configuracao.taxaEntrega();
    }

    /** Pedido dentro de uma conta de mesa já autorizada pelo chamador (MesaService). */
    @Transactional
    public PedidoCriadoResponse criarParaMesa(ContaMesa conta, List<ItemRequest> itens, String observacao, String chaveIdempotencia) {
        abertura.exigirCaixaAberto();
        if (conta.getEstado() != EstadoContaMesa.ABERTA) {
            throw new NegocioException("A conta de mesa não está aberta para novos pedidos");
        }
        String chave = normalizarChave(chaveIdempotencia);
        if (chave != null) {
            var existente = pedidos.findByChaveIdempotencia(chave);
            if (existente.isPresent()) {
                return new PedidoCriadoResponse(mapper.toResponse(existente.get(), true), null, null);
            }
        }
        Pedido p = new Pedido();
        p.setChaveIdempotencia(chave);
        p.setModalidade(Modalidade.MESA);
        p.setContaMesaId(conta.getId());
        p.setNomeResponsavel(conta.getNomeResponsavel());
        p.setCpfResponsavel(conta.getCpfResponsavel());
        p.setObservacao(observacao);
        BigDecimal subtotal = montarItens(p, itens);
        p.setSubtotal(subtotal);
        p.setTaxaEntrega(Dinheiro.ZERO);
        p.setTotal(subtotal);
        persistir(p);
        String acompanhamento = codigos.gerarCodigoAcompanhamento(p);
        return new PedidoCriadoResponse(mapper.toResponse(p, true), acompanhamento, null);
    }

    @Transactional(readOnly = true)
    public PedidoResponse obter(Long id) {
        return mapper.toResponse(pedidos.findById(id).orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado")), true);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PedidoResponse> listar(EstadoPedido estado, Modalidade modalidade,
                                                                       Situacao situacao, LocalDate de, LocalDate ate,
                                                                       org.springframework.data.domain.Pageable pageable) {
        List<EstadoPedido> estados = estadosDe(estado, situacao);
        if (estados.isEmpty()) {
            return org.springframework.data.domain.Page.empty(pageable);
        }
        LocalDateTime ini = de == null ? null : de.atStartOfDay();
        LocalDateTime fim = ate == null ? null : ate.plusDays(1).atStartOfDay(); // "até" inclui o dia inteiro
        return pedidos.buscar(estados, modalidade, ini, fim, pageable).map(p -> mapper.toResponse(p, false));
    }

    /** ANDAMENTO: ainda não concluídos nem cancelados. FINALIZADOS: concluídos e cancelados (histórico). */
    public enum Situacao {
        ANDAMENTO, FINALIZADOS
    }

    public static final List<EstadoPedido> FINAIS = List.of(EstadoPedido.CONCLUIDO, EstadoPedido.CANCELADO);

    static List<EstadoPedido> estadosDe(EstadoPedido estado, Situacao situacao) {
        List<EstadoPedido> base = situacao == null ? List.of(EstadoPedido.values())
                : java.util.Arrays.stream(EstadoPedido.values())
                        .filter(e -> FINAIS.contains(e) == (situacao == Situacao.FINALIZADOS)).toList();
        return estado == null ? base : base.stream().filter(e -> e == estado).toList();
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> historicoDoCliente(Long clienteId) {
        return pedidos.findByClienteIdOrderByCriadoEmDesc(clienteId).stream().map(p -> mapper.toResponse(p, false)).toList();
    }

    // ---------------------------------------------------------------- internos

    private void persistir(Pedido p) {
        p.setCaixaId(abertura.caixaAberto()
                .orElseThrow(() -> new NegocioException(HttpStatus.CONFLICT, "O caixa foi fechado antes da confirmação da venda"))
                .getId());
        p.setNumeroPedido(gerarNumero());
        pedidos.save(p);
        estados.registrarHistorico(p.getId(), null, EstadoPedido.AGUARDANDO_CONFIRMACAO, null, false, null);
    }

    private BigDecimal montarItens(Pedido p, List<ItemRequest> itens) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemRequest req : itens) {
            ItemCotado c = cotacao.resolver(req);
            ItemPedido item = new ItemPedido();
            item.setPedido(p);
            item.setProdutoId(c.produtoId());
            item.setNomeProdutoHistorico(c.nomeProduto());
            item.setTipoProdutoHistorico(c.tipo().name());
            item.setQuantidade(c.quantidade());
            item.setPrecoUnitario(c.precoUnitario());
            item.setSubtotal(c.subtotal());
            item.setPrecoBaseHistorico(c.precoBase());
            item.setEstrategiaHistorica(c.estrategia() == null ? null : c.estrategia().name());
            item.setParticipaPrecoBaseHistorico(c.participaPrecoBase());
            for (LinhaEscolha l : c.escolhas()) {
                EscolhaItemPedido e = new EscolhaItemPedido();
                e.setItem(item);
                e.setGrupoId(l.grupoId());
                e.setOpcaoId(l.opcaoId());
                e.setNomeGrupoHistorico(l.nomeGrupo());
                e.setNomeOpcaoHistorico(l.nomeOpcao());
                e.setModalidadeCobrancaHistorica(l.modalidade().name());
                e.setQuantidade(l.quantidade());
                e.setPrecoUnitarioAplicado(l.precoUnitario());
                e.setSubtotal(l.subtotal());
                item.getEscolhas().add(e);
            }
            for (LinhaAdicional l : c.adicionais()) {
                AdicionalItemPedido a = new AdicionalItemPedido();
                a.setItem(item);
                a.setAdicionalId(l.adicionalId());
                a.setNomeHistorico(l.nome());
                a.setQuantidade(l.quantidade());
                a.setPrecoUnitarioAplicado(l.precoUnitario());
                a.setSubtotal(l.subtotal());
                item.getAdicionais().add(a);
            }
            p.getItens().add(item);
            subtotal = subtotal.add(c.subtotal());
        }
        return Dinheiro.arredondar(subtotal);
    }

    private String gerarNumero() {
        String prefixo = "P" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            StringBuilder sb = new StringBuilder(prefixo);
            for (int i = 0; i < 5; i++) {
                sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
            }
            if (!pedidos.existsByNumeroPedido(sb.toString())) {
                return sb.toString();
            }
        }
        throw new IllegalStateException("Não foi possível gerar o número do pedido");
    }

    private static String normalizarChave(String chave) {
        if (chave == null || chave.isBlank()) {
            return null;
        }
        String c = chave.trim();
        if (c.length() > 64) {
            throw new NegocioException("Idempotency-Key deve ter no máximo 64 caracteres");
        }
        return c;
    }
}
