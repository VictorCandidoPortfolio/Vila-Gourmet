package com.vilagourmet.dto;

import com.vilagourmet.dto.CotacaoDTO.SelecaoAdicional;
import com.vilagourmet.dto.CotacaoDTO.SelecaoOpcao;
import com.vilagourmet.model.enums.EstadoCancelamento;
import com.vilagourmet.model.enums.EstadoPedido;
import com.vilagourmet.model.enums.Modalidade;
import com.vilagourmet.model.enums.MetodoPagamento;
import com.vilagourmet.model.enums.TipoSolicitante;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PedidoDTO {

    private PedidoDTO() {
    }

    /**
     * Item do pedido. Se {@code cotacaoToken} for informado, o preço cotado é honrado
     * (as demais seleções são ignoradas); caso contrário o servidor calcula agora.
     */
    public record ItemRequest(
            Long produtoId,
            @Min(1) Integer quantidade,
            @Valid List<SelecaoOpcao> escolhas,
            @Valid List<SelecaoAdicional> adicionais,
            String cotacaoToken) {
    }

    /** Pedido de ENTREGA ou RETIRADA. Pedidos de mesa usam {@link MesaDTO.MesaPedidoRequest}. */
    public record PedidoRequest(
            @NotNull Modalidade modalidade,
            @NotEmpty @Valid List<ItemRequest> itens,
            Long enderecoId,
            String nomeResponsavel,
            String cpfResponsavel,
            MetodoPagamento metodoPagamento,
            String observacao) {
    }

    public record EnderecoSnapshot(String cep, String logradouro, String numero, String complemento,
                                   String bairro, String cidade, String estado) {
    }

    public record EscolhaResponse(String grupo, String opcao, String modalidadeCobranca, int quantidade,
                                  BigDecimal precoUnitario, BigDecimal subtotal) {
    }

    public record AdicionalResponse(String nome, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {
    }

    public record ItemResponse(String nomeProduto, String tipo, int quantidade, BigDecimal precoUnitario,
                               BigDecimal subtotal, List<EscolhaResponse> escolhas, List<AdicionalResponse> adicionais) {
    }

    public record HistoricoResponse(String estadoAnterior, String estadoNovo, boolean conclusaoManual,
                                    String justificativa, Long funcionarioId, LocalDateTime em) {
    }

    public record EdicaoVendaResponse(Long funcionarioId, LocalDateTime em, String justificativa,
                                      String nomeAnterior, String nomeNovo, boolean cpfAlterado,
                                      String observacaoAnterior, String observacaoNova,
                                      String metodoAnterior, String metodoNovo) {
    }

    /** CPF sempre mascarado. */
    public record PedidoResponse(
            Long id, String numeroPedido, Modalidade modalidade, EstadoPedido estado,
            String nomeResponsavel, String cpfResponsavel, EnderecoSnapshot endereco,
            BigDecimal subtotal, BigDecimal taxaEntrega, BigDecimal total, String observacao,
            LocalDateTime criadoEm, String codigoConta, List<ItemResponse> itens,
            PagamentoDTO.PagamentoResponse pagamento, List<HistoricoResponse> historico,
            LocalDateTime ultimaEdicaoCaixaEm, String justificativaUltimaEdicao,
            List<EdicaoVendaResponse> edicoesCaixa) {
    }

    /** Códigos em texto puro só existem nesta resposta: o banco guarda apenas o HMAC. */
    public record PedidoCriadoResponse(PedidoResponse pedido, String codigoAcompanhamento, String codigoConfirmacao) {
    }

    public record TransicaoRequest(@NotNull EstadoPedido estado) {
    }

    public record ConclusaoCodigoRequest(@NotBlank String codigo) {
    }

    public record ConclusaoManualRequest(@NotBlank String justificativa) {
    }

    public record CancelarRequest(@NotBlank String justificativa) {
    }

    /** Identificação pública do pedido: código de acompanhamento OU número + CPF. */
    public record RastreamentoRequest(String codigo, String numeroPedido, String cpf) {
    }

    /** Visão pública mínima: sem CPF, endereço ou código de confirmação. */
    public record RastreamentoResponse(String numeroPedido, Modalidade modalidade, EstadoPedido estado,
                                       BigDecimal total, String pagamento, List<String> itens,
                                       List<HistoricoPublico> historico) {
    }

    public record HistoricoPublico(String estado, LocalDateTime em) {
    }

    public record NovoCodigoConfirmacaoResponse(String codigoConfirmacao) {
    }

    public record CancelamentoResponse(Long id, Long pedidoId, String numeroPedido, TipoSolicitante solicitadoPor,
                                       EstadoCancelamento estado, LocalDateTime solicitadoEm,
                                       LocalDateTime decididoEm, String justificativa, EstadoPedido estadoPedido) {
    }

    public record DecisaoCancelamentoRequest(@NotNull Boolean aprovar, String justificativa) {
    }
}
