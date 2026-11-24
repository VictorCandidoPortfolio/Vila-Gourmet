package com.vilagourmet.service;

import com.vilagourmet.dto.MesaDTO.AbrirMesaRequest;
import com.vilagourmet.dto.MesaDTO.AcessoMesaResponse;
import com.vilagourmet.dto.MesaDTO.ContaMesaResponse;
import com.vilagourmet.dto.MesaDTO.FechamentoResponse;
import com.vilagourmet.dto.MesaDTO.HistoricoMesaResponse;
import com.vilagourmet.dto.MesaDTO.MesaPedidoRequest;
import com.vilagourmet.dto.PedidoDTO.PedidoCriadoResponse;
import com.vilagourmet.dto.PedidoDTO.PedidoResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.ContaMesa;
import com.vilagourmet.model.FechamentoMesa;
import com.vilagourmet.model.HistoricoContaMesa;
import com.vilagourmet.model.Pagamento;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.enums.EstadoContaMesa;
import com.vilagourmet.model.enums.EstadoFechamento;
import com.vilagourmet.model.enums.EstadoPagamento;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.repository.ContaMesaRepository;
import com.vilagourmet.repository.FechamentoMesaRepository;
import com.vilagourmet.repository.HistoricoContaMesaRepository;
import com.vilagourmet.repository.PagamentoRepository;
import com.vilagourmet.repository.PedidoRepository;
import com.vilagourmet.util.Cpf;
import com.vilagourmet.util.Dinheiro;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contas de mesa: acumulam vários pedidos (cada um com número e estado próprios) e são pagas no
 * fechamento. O CPF identifica o responsável e só pode estar em uma conta ativa; o código localiza-a.
 * Política V1: conta ENCERRADA não é reaberta; um fechamento cancelado devolve a conta a ABERTA.
 */
@Service
public class MesaService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final List<EstadoContaMesa> ESTADOS_ATIVOS =
            List.of(EstadoContaMesa.ABERTA, EstadoContaMesa.EM_FECHAMENTO);
    private static final String CONTA_ATIVA =
            "Já existe uma conta de mesa ativa para este CPF. Use a opção de acessar a conta aberta.";

    private final SecureRandom random = new SecureRandom();
    private final ContaMesaRepository contas;
    private final HistoricoContaMesaRepository historicos;
    private final FechamentoMesaRepository fechamentos;
    private final PagamentoRepository pagamentos;
    private final PedidoRepository pedidos;
    private final PedidoService pedidoService;
    private final PedidoMapper mapper;
    private final RateLimiterService limiter;
    private final AberturaLojaService abertura;

    public MesaService(ContaMesaRepository contas, HistoricoContaMesaRepository historicos,
                       FechamentoMesaRepository fechamentos, PagamentoRepository pagamentos, PedidoRepository pedidos,
                       PedidoService pedidoService, PedidoMapper mapper, RateLimiterService limiter,
                       AberturaLojaService abertura) {
        this.contas = contas;
        this.historicos = historicos;
        this.fechamentos = fechamentos;
        this.pagamentos = pagamentos;
        this.pedidos = pedidos;
        this.pedidoService = pedidoService;
        this.mapper = mapper;
        this.limiter = limiter;
        this.abertura = abertura;
    }

    /** Qualquer pessoa pode iniciar o consumo, sem conta de cliente. */
    @Transactional
    public ContaMesaResponse abrir(AbrirMesaRequest req) {
        abertura.exigirVendasOnline();
        String cpf = Cpf.exigirValido(req.cpf());
        exigirSemContaAtiva(cpf);
        ContaMesa c = new ContaMesa();
        c.setNomeResponsavel(req.nome().trim());
        c.setCpfResponsavel(cpf);
        c.setCodigoConta(gerarCodigo());
        salvarNovaConta(c);
        registrarHistorico(c.getId(), null, EstadoContaMesa.ABERTA, null, null);
        return montar(c);
    }

    /** Recupera no aparelho uma conta ainda ativa usando o CPF do responsável. */
    @Transactional(readOnly = true)
    public AcessoMesaResponse acessarPorCpf(String cpfInformado, String ip) {
        abertura.exigirVendasOnline();
        String chaveLimite = "mesa-acesso:" + ip;
        limiter.exigirDisponivel(chaveLimite);
        String cpf = Cpf.normalizar(cpfInformado);
        ContaMesa conta = cpf == null ? null
                : contas.findFirstByCpfResponsavelAndEstadoInOrderByAbertaEmDesc(cpf, ESTADOS_ATIVOS).orElse(null);
        if (conta == null) {
            limiter.registrarFalha(chaveLimite);
            throw new NaoEncontradoException("Não há conta de mesa ativa para este CPF");
        }
        limiter.limpar(chaveLimite);
        return new AcessoMesaResponse(conta.getCodigoConta(), conta.getNomeResponsavel(), conta.getEstado());
    }

    /** Funcionário abre a conta de mesa no caixa: só o nome é obrigatório; CPF opcional. */
    @Transactional
    public ContaMesaResponse abrirPeloCaixa(String nome, String cpf) {
        abertura.exigirCaixaAberto();
        if (nome == null || nome.isBlank()) {
            throw new NegocioException("Informe o nome do responsável");
        }
        ContaMesa c = new ContaMesa();
        c.setNomeResponsavel(nome.trim());
        String cpfNormalizado = cpf == null || cpf.isBlank() ? null : Cpf.exigirValido(cpf);
        exigirSemContaAtiva(cpfNormalizado);
        c.setCpfResponsavel(cpfNormalizado);
        c.setCodigoConta(gerarCodigo());
        salvarNovaConta(c);
        registrarHistorico(c.getId(), null, EstadoContaMesa.ABERTA, null, null);
        return montar(c);
    }

    /** Visitante lança pedido informando o código da conta e o CPF do responsável. */
    @Transactional
    public PedidoCriadoResponse pedidoDoResponsavel(String codigo, MesaPedidoRequest req, String ip, String chave) {
        abertura.exigirVendasOnline();
        String chaveLimite = "mesa:" + ip;
        limiter.exigirDisponivel(chaveLimite);
        ContaMesa c = contas.findByCodigoConta(codigo == null ? "" : codigo.trim().toUpperCase()).orElse(null);
        String cpf = Cpf.normalizar(req.cpfResponsavel());
        if (c == null || cpf == null || c.getCpfResponsavel() == null || !MessageDigest.isEqual(
                c.getCpfResponsavel().getBytes(StandardCharsets.UTF_8), cpf.getBytes(StandardCharsets.UTF_8))) {
            limiter.registrarFalha(chaveLimite);
            throw new NaoEncontradoException("Conta de mesa não encontrada");
        }
        return pedidoService.criarParaMesa(c, req.itens(), req.observacao(), chave);
    }

    /** Funcionário localiza a conta pelo código e adiciona o pedido. */
    @Transactional
    public PedidoCriadoResponse pedidoPeloFuncionario(String codigo, MesaPedidoRequest req, String chave) {
        return pedidoService.criarParaMesa(porCodigo(codigo), req.itens(), req.observacao(), chave);
    }

    @Transactional(readOnly = true)
    public List<ContaMesaResponse> listar(EstadoContaMesa estado) {
        List<ContaMesa> lista = estado == null ? contas.findAllByOrderByAbertaEmDesc() : contas.findByEstadoOrderByAbertaEmDesc(estado);
        return lista.stream().map(this::montar).toList();
    }

    @Transactional(readOnly = true)
    public ContaMesaResponse obter(String codigo) {
        return montar(porCodigo(codigo));
    }

    /** Move a conta para EM_FECHAMENTO e gera o fechamento com o total dos pedidos não cancelados. */
    @Transactional
    public ContaMesaResponse iniciarFechamento(String codigo, Long funcionarioId) {
        ContaMesa c = porCodigo(codigo);
        if (c.getEstado() != EstadoContaMesa.ABERTA) {
            throw new NegocioException(HttpStatus.CONFLICT, "A conta não está aberta");
        }
        List<Pedido> validos = pedidos.findByContaMesaIdOrderByIdAsc(c.getId()).stream()
                .filter(p -> p.getEstado() != EstadoPedido.CANCELADO).toList();
        if (validos.isEmpty()) {
            throw new NegocioException("A conta não possui pedidos para fechar");
        }
        List<String> pendentes = validos.stream()
                .filter(p -> p.getEstado() != EstadoPedido.SERVIDO && p.getEstado() != EstadoPedido.CONCLUIDO)
                .map(Pedido::getNumeroPedido).toList();
        if (!pendentes.isEmpty()) {
            throw new NegocioException("Há pedidos ainda não servidos: " + String.join(", ", pendentes));
        }
        FechamentoMesa f = new FechamentoMesa();
        f.setContaMesaId(c.getId());
        f.setTotalFechamento(somar(validos));
        fechamentos.save(f);
        c.setEstado(EstadoContaMesa.EM_FECHAMENTO);
        registrarHistorico(c.getId(), EstadoContaMesa.ABERTA, EstadoContaMesa.EM_FECHAMENTO, funcionarioId, null);
        return montar(c);
    }

    @Transactional
    public ContaMesaResponse cancelarFechamento(String codigo, Long funcionarioId, String justificativa) {
        ContaMesa c = porCodigo(codigo);
        FechamentoMesa f = fechamentos.findByContaMesaIdAndEstado(c.getId(), EstadoFechamento.PENDENTE)
                .orElseThrow(() -> new NegocioException("Não há fechamento pendente nesta conta"));
        if (pagamentos.findByFechamentoMesaIdAndEstado(f.getId(), EstadoPagamento.CONFIRMADO).isPresent()) {
            throw new NegocioException("O fechamento já possui pagamento confirmado");
        }
        f.setEstado(EstadoFechamento.CANCELADO);
        c.setEstado(EstadoContaMesa.ABERTA);
        registrarHistorico(c.getId(), EstadoContaMesa.EM_FECHAMENTO, EstadoContaMesa.ABERTA, funcionarioId, justificativa);
        return montar(c);
    }

    /** Chamado pelo PagamentoService após o pagamento do fechamento. */
    @Transactional
    public void encerrar(Long contaId, Long funcionarioId) {
        ContaMesa c = contas.findById(contaId).orElseThrow(() -> new NaoEncontradoException("Conta de mesa não encontrada"));
        c.setEstado(EstadoContaMesa.ENCERRADA);
        c.setEncerradaEm(LocalDateTime.now());
        registrarHistorico(c.getId(), EstadoContaMesa.EM_FECHAMENTO, EstadoContaMesa.ENCERRADA, funcionarioId, null);
    }

    /** Fechamento do caixa: conta aberta cujos pedidos foram cancelados é encerrada sem cobrança. */
    @Transactional
    public void encerrarSemCobranca(Long contaId, Long funcionarioId, String justificativa) {
        ContaMesa c = contas.findById(contaId).orElseThrow(() -> new NaoEncontradoException("Conta de mesa não encontrada"));
        encerrarSemCobranca(c, funcionarioId, justificativa);
    }

    /** Ação manual para corrigir uma conta aberta por engano, sem criar fechamento ou pagamento. */
    @Transactional
    public ContaMesaResponse encerrarSemFechamento(String codigo, Long funcionarioId, String justificativa) {
        ContaMesa c = porCodigo(codigo);
        encerrarSemCobranca(c, funcionarioId, justificativa);
        return montar(c);
    }

    private void encerrarSemCobranca(ContaMesa c, Long funcionarioId, String justificativa) {
        if (c.getEstado() != EstadoContaMesa.ABERTA) {
            throw new NegocioException("Só uma conta aberta pode ser encerrada sem cobrança");
        }
        List<Pedido> comConsumo = pedidos.findByContaMesaIdOrderByIdAsc(c.getId()).stream()
                .filter(p -> p.getEstado() != EstadoPedido.CANCELADO).toList();
        if (!comConsumo.isEmpty()) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "A conta possui pedidos. Cancele-os ou faça o fechamento antes de encerrar a mesa.");
        }
        c.setEstado(EstadoContaMesa.ENCERRADA);
        c.setEncerradaEm(LocalDateTime.now());
        registrarHistorico(c.getId(), EstadoContaMesa.ABERTA, EstadoContaMesa.ENCERRADA, funcionarioId, justificativa);
    }

    // ---------------------------------------------------------------- internos

    private ContaMesa porCodigo(String codigo) {
        return contas.findByCodigoConta(codigo == null ? "" : codigo.trim().toUpperCase())
                .orElseThrow(() -> new NaoEncontradoException("Conta de mesa não encontrada"));
    }

    private void exigirSemContaAtiva(String cpf) {
        if (cpf != null && contas.existsByCpfResponsavelAndEstadoIn(cpf, ESTADOS_ATIVOS)) {
            throw new NegocioException(HttpStatus.CONFLICT, CONTA_ATIVA);
        }
    }

    /** O índice único no banco fecha a janela de corrida entre duas aberturas simultâneas. */
    private void salvarNovaConta(ContaMesa conta) {
        try {
            contas.saveAndFlush(conta);
        } catch (DataIntegrityViolationException e) {
            throw new NegocioException(HttpStatus.CONFLICT, CONTA_ATIVA);
        }
    }

    private ContaMesaResponse montar(ContaMesa c) {
        List<Pedido> todos = pedidos.findByContaMesaIdOrderByIdAsc(c.getId());
        List<PedidoResponse> respostas = todos.stream().map(p -> mapper.toResponse(p, false)).toList();
        FechamentoResponse fechamento = fechamentos.findByContaMesaIdOrderByIdDesc(c.getId()).stream().findFirst()
                .map(this::paraResponse).orElse(null);
        List<HistoricoMesaResponse> historico = historicos.findByContaMesaIdOrderByIdAsc(c.getId()).stream()
                .map(h -> new HistoricoMesaResponse(h.getEstadoAnterior(), h.getEstadoNovo(), h.getFuncionarioId(),
                        h.getJustificativa(), h.getCriadoEm())).toList();
        return new ContaMesaResponse(c.getId(), c.getCodigoConta(), c.getNomeResponsavel(), Cpf.mascarar(c.getCpfResponsavel()),
                c.getEstado(), c.getAbertaEm(), c.getEncerradaEm(), somar(todos), respostas, fechamento, historico);
    }

    private FechamentoResponse paraResponse(FechamentoMesa f) {
        List<Pagamento> lista = pagamentos.findByFechamentoMesaIdOrderByIdDesc(f.getId());
        Pagamento escolhido = lista.stream().filter(x -> x.getEstado() == EstadoPagamento.CONFIRMADO).findFirst()
                .orElse(lista.isEmpty() ? null : lista.get(0));
        return new FechamentoResponse(f.getId(), f.getTotalFechamento(), f.getEstado(), f.getCriadoEm(), f.getConcluidoEm(),
                PagamentoService.paraResponse(escolhido));
    }

    /** Soma dos pedidos não cancelados: o total da conta nunca conta o mesmo pedido duas vezes. */
    private BigDecimal somar(List<Pedido> lista) {
        return Dinheiro.arredondar(lista.stream().filter(p -> p.getEstado() != EstadoPedido.CANCELADO)
                .map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void registrarHistorico(Long contaId, EstadoContaMesa anterior, EstadoContaMesa novo, Long funcionarioId, String justificativa) {
        HistoricoContaMesa h = new HistoricoContaMesa();
        h.setContaMesaId(contaId);
        h.setEstadoAnterior(anterior == null ? null : anterior.name());
        h.setEstadoNovo(novo.name());
        h.setFuncionarioId(funcionarioId);
        h.setJustificativa(justificativa);
        historicos.save(h);
    }

    private String gerarCodigo() {
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            StringBuilder sb = new StringBuilder("M");
            for (int i = 0; i < 6; i++) {
                sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
            }
            if (!contas.existsByCodigoConta(sb.toString())) {
                return sb.toString();
            }
        }
        throw new IllegalStateException("Não foi possível gerar o código da conta");
    }
}
