package com.vilagourmet.dto;

import com.vilagourmet.dto.PedidoDTO.ItemRequest;
import com.vilagourmet.model.enums.MetodoPagamento;
import com.vilagourmet.model.enums.Modalidade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class CaixaDTO {

    private CaixaDTO() {
    }

    /** Situação pública da pizzaria (para a loja): aberta somente com um caixa aberto. */
    public record LojaStatusResponse(boolean aberta, LocalDateTime abertaEm) {
    }

    /**
     * {@code pedidosEmAberto}: pedidos não concluídos, que serão cancelados no fechamento.
     * {@code bloqueios}: motivos que impedem o fechamento (pedidos que não podem ser cancelados).
     */
    public record CaixaStatusResponse(boolean aberto, LocalDateTime abertoEm, String abertoPor,
                                      int pedidosEmAberto, List<String> bloqueios) {
    }

    /** O fechamento com pedidos em aberto exige confirmação explícita de que eles serão cancelados. */
    public record FecharCaixaRequest(Boolean confirmarCancelamentos) {
    }

    public record FechamentoCaixaResponse(LocalDateTime fechadoEm, int pedidosCancelados, int contasEncerradas) {
    }

    public record EnderecoCaixaRequest(
            @NotBlank String cep,
            @NotBlank @Size(max = 200) String logradouro,
            @NotBlank @Size(max = 20) String numero,
            @Size(max = 100) String complemento,
            @NotBlank @Size(max = 100) String bairro,
            @NotBlank @Size(max = 100) String cidade,
            @NotBlank @Size(min = 2, max = 2) String estado) {
    }

    /** Abertura de conta de mesa pelo funcionário: só o nome é obrigatório; o CPF é opcional. */
    public record AbrirMesaCaixaRequest(@NotBlank @Size(max = 150) String nome, String cpf) {
    }

    /**
     * Pedido lançado pelo funcionário: basta o nome do cliente (sem cadastro; CPF opcional).
     * ENTREGA exige o endereço digitado (dentro da área atendida); MESA, o código de uma conta de mesa aberta.
     */
    public record PedidoCaixaRequest(
            @NotNull Modalidade modalidade,
            @NotEmpty @Valid List<ItemRequest> itens,
            String nomeResponsavel,
            String cpfResponsavel,
            @Valid EnderecoCaixaRequest endereco,
            String codigoConta,
            MetodoPagamento metodoPagamento,
            String observacao) {
    }
}
