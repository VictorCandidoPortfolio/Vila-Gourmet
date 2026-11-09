package com.vilagourmet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vilagourmet.dto.ClienteDTO.ClienteResponse;
import com.vilagourmet.dto.ClienteDTO.EnderecoRequest;
import com.vilagourmet.dto.ClienteDTO.EnderecoResponse;
import com.vilagourmet.dto.ClienteDTO.PizzaSalvaRequest;
import com.vilagourmet.dto.ClienteDTO.PizzaSalvaResponse;
import com.vilagourmet.dto.CotacaoDTO.SelecaoOpcao;
import com.vilagourmet.dto.PedidoDTO.NovoCodigoConfirmacaoResponse;
import com.vilagourmet.exception.NaoEncontradoException;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Cliente;
import com.vilagourmet.model.Endereco;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.PizzaSalva;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.model.enums.Modalidade;
import com.vilagourmet.repository.ClienteRepository;
import com.vilagourmet.repository.EnderecoRepository;
import com.vilagourmet.repository.PedidoRepository;
import com.vilagourmet.repository.PizzaSalvaRepository;
import com.vilagourmet.repository.ProdutoRepository;
import com.vilagourmet.util.Cpf;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Área do cliente: dados cadastrais, endereços (com padrão único), pizzas salvas e códigos dos pedidos. */
@Service
public class ClienteService {

    private final ClienteRepository clientes;
    private final EnderecoRepository enderecos;
    private final PizzaSalvaRepository pizzas;
    private final ProdutoRepository produtos;
    private final PedidoRepository pedidos;
    private final CodigoService codigos;
    private final ObjectMapper mapper;

    public ClienteService(ClienteRepository clientes, EnderecoRepository enderecos, PizzaSalvaRepository pizzas,
                          ProdutoRepository produtos, PedidoRepository pedidos, CodigoService codigos, ObjectMapper mapper) {
        this.clientes = clientes;
        this.enderecos = enderecos;
        this.pizzas = pizzas;
        this.produtos = produtos;
        this.pedidos = pedidos;
        this.codigos = codigos;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ClienteResponse perfil(Long clienteId) {
        Cliente c = cliente(clienteId);
        return new ClienteResponse(c.getId(), c.getNome(), c.getConta().getEmail(), Cpf.mascarar(c.getCpf()));
    }

    // ---------------------------------------------------------------- endereços

    @Transactional(readOnly = true)
    public List<EnderecoResponse> listarEnderecos(Long clienteId) {
        return enderecos.findByClienteIdAndAtivoTrueOrderByIdAsc(clienteId).stream().map(this::paraResponse).toList();
    }

    @Transactional
    public EnderecoResponse criarEndereco(Long clienteId, EnderecoRequest req) {
        Endereco e = new Endereco();
        e.setCliente(cliente(clienteId));
        preencher(e, req);
        boolean primeiro = enderecos.findByClienteIdAndAtivoTrueOrderByIdAsc(clienteId).isEmpty();
        e.setPadrao(false);
        enderecos.saveAndFlush(e);
        if (primeiro || req.padrao()) {
            definirPadrao(clienteId, e);
        }
        return paraResponse(e);
    }

    @Transactional
    public EnderecoResponse atualizarEndereco(Long clienteId, Long id, EnderecoRequest req) {
        Endereco e = endereco(clienteId, id);
        preencher(e, req);
        if (req.padrao() && !e.isPadrao()) {
            definirPadrao(clienteId, e);
        } else if (!req.padrao() && e.isPadrao()) {
            throw new NegocioException("Defina outro endereço como padrão em vez de remover o padrão atual");
        }
        return paraResponse(e);
    }

    /** Endereço usado em pedidos permanece no snapshot; aqui apenas desativamos o cadastro. */
    @Transactional
    public void removerEndereco(Long clienteId, Long id) {
        Endereco e = endereco(clienteId, id);
        boolean eraPadrao = e.isPadrao();
        e.setAtivo(false);
        e.setPadrao(false);
        enderecos.saveAndFlush(e);
        if (eraPadrao) {
            enderecos.findByClienteIdAndAtivoTrueOrderByIdAsc(clienteId).stream().findFirst().ifPresent(o -> definirPadrao(clienteId, o));
        }
    }

    private void definirPadrao(Long clienteId, Endereco novo) {
        enderecos.findByClienteIdAndPadraoTrueAndAtivoTrue(clienteId).ifPresent(atual -> {
            atual.setPadrao(false);
            enderecos.saveAndFlush(atual); // antes de marcar o novo: a restrição de unicidade só admite um padrão
        });
        novo.setPadrao(true);
        enderecos.saveAndFlush(novo);
    }

    private void preencher(Endereco e, EnderecoRequest req) {
        String cep = req.cep().replaceAll("\\D", "");
        if (cep.length() != 8) {
            throw new NegocioException("CEP inválido");
        }
        e.setIdentificacao(req.identificacao());
        e.setCep(cep);
        e.setLogradouro(req.logradouro().trim());
        e.setNumero(req.numero().trim());
        e.setComplemento(req.complemento());
        e.setBairro(req.bairro().trim());
        e.setCidade(req.cidade().trim());
        e.setEstado(req.estado().trim().toUpperCase());
    }

    // ---------------------------------------------------------------- pizzas salvas

    @Transactional(readOnly = true)
    public List<PizzaSalvaResponse> listarPizzas(Long clienteId) {
        return pizzas.findByClienteIdOrderByIdDesc(clienteId).stream().map(this::paraResponse).toList();
    }

    @Transactional
    public PizzaSalvaResponse salvarPizza(Long clienteId, PizzaSalvaRequest req) {
        if (!produtos.existsById(req.produtoId())) {
            throw new NaoEncontradoException("Produto não encontrado");
        }
        PizzaSalva p = new PizzaSalva();
        p.setClienteId(clienteId);
        p.setProdutoId(req.produtoId());
        p.setNome(req.nome().trim());
        try {
            p.setSelecaoJson(mapper.writeValueAsString(req.escolhas() == null ? List.of() : req.escolhas()));
        } catch (JsonProcessingException ex) {
            throw new NegocioException("Seleção inválida");
        }
        return paraResponse(pizzas.save(p));
    }

    @Transactional
    public void removerPizza(Long clienteId, Long id) {
        pizzas.delete(pizzas.findByIdAndClienteId(id, clienteId).orElseThrow(() -> new NaoEncontradoException("Pizza salva não encontrada")));
    }

    // ---------------------------------------------------------------- pedidos

    /** Gera novo código de confirmação (o anterior é invalidado). Só para entrega/retirada em andamento. */
    @Transactional
    public NovoCodigoConfirmacaoResponse novoCodigoConfirmacao(Long clienteId, Long pedidoId) {
        Pedido p = pedidos.findById(pedidoId).filter(x -> clienteId.equals(x.getClienteId()))
                .orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado"));
        if (p.getModalidade() == Modalidade.MESA || p.getEstado() == EstadoPedido.CONCLUIDO || p.getEstado() == EstadoPedido.CANCELADO) {
            throw new NegocioException("Não é possível gerar código para este pedido");
        }
        return new NovoCodigoConfirmacaoResponse(codigos.gerarCodigoConfirmacao(p));
    }

    // ---------------------------------------------------------------- internos

    private Cliente cliente(Long id) {
        return clientes.findById(id).orElseThrow(() -> new NaoEncontradoException("Cliente não encontrado"));
    }

    private Endereco endereco(Long clienteId, Long id) {
        return enderecos.findByIdAndClienteIdAndAtivoTrue(id, clienteId).orElseThrow(() -> new NaoEncontradoException("Endereço não encontrado"));
    }

    private EnderecoResponse paraResponse(Endereco e) {
        return new EnderecoResponse(e.getId(), e.getIdentificacao(), e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getEstado(), e.isPadrao());
    }

    private PizzaSalvaResponse paraResponse(PizzaSalva p) {
        try {
            List<SelecaoOpcao> escolhas = mapper.readValue(p.getSelecaoJson(), new TypeReference<>() { });
            return new PizzaSalvaResponse(p.getId(), p.getNome(), p.getProdutoId(), escolhas, p.getCriadoEm());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Seleção salva corrompida", ex);
        }
    }
}
