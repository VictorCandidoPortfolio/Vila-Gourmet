package com.vilagourmet.service;

import com.vilagourmet.config.AppProperties;
import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.CodigoAcompanhamento;
import com.vilagourmet.model.CodigoConfirmacao;
import com.vilagourmet.model.Pedido;
import com.vilagourmet.model.enums.Modalidade;
import com.vilagourmet.model.enums.TipoCodigo;
import com.vilagourmet.repository.CodigoAcompanhamentoRepository;
import com.vilagourmet.repository.CodigoConfirmacaoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Códigos de acompanhamento (secreto longo) e de confirmação de recebimento/retirada (6 dígitos).
 * O banco guarda apenas HMAC-SHA256 com o segredo do servidor: determinístico (permite a busca do
 * código de acompanhamento) e inviável de reverter por força bruta sem o segredo. O código de
 * confirmação tem validade, uso único e limite de tentativas.
 */
@Service
public class CodigoService {

    private final SecureRandom random = new SecureRandom();
    private final CodigoAcompanhamentoRepository acompanhamentos;
    private final CodigoConfirmacaoRepository confirmacoes;
    private final AppProperties props;
    private final SecretKeySpec chaveHmac;

    public CodigoService(CodigoAcompanhamentoRepository acompanhamentos, CodigoConfirmacaoRepository confirmacoes,
                         AppProperties props) {
        this.acompanhamentos = acompanhamentos;
        this.confirmacoes = confirmacoes;
        this.props = props;
        this.chaveHmac = new SecretKeySpec(("codigos|" + props.jwt().secret()).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Transactional
    public String gerarCodigoAcompanhamento(Pedido pedido) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        CodigoAcompanhamento c = new CodigoAcompanhamento();
        c.setPedidoId(pedido.getId());
        c.setCodigoHash(hmac("acomp:" + codigo));
        acompanhamentos.save(c);
        return codigo;
    }

    public Optional<Long> localizarPedidoPorAcompanhamento(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return acompanhamentos.findByCodigoHashAndAtivoTrue(hmac("acomp:" + codigo.trim())).map(CodigoAcompanhamento::getPedidoId);
    }

    /** Gera (ou regenera, invalidando o anterior) o código de confirmação de entrega/retirada. */
    @Transactional
    public String gerarCodigoConfirmacao(Pedido pedido) {
        if (pedido.getModalidade() == Modalidade.MESA) {
            throw new NegocioException("Pedidos de mesa não usam código de confirmação");
        }
        confirmacoes.findByPedidoIdAndAtivoTrue(pedido.getId()).ifPresent(anterior -> {
            anterior.setAtivo(false);
            confirmacoes.saveAndFlush(anterior); // libera a unicidade do código ativo antes de inserir o novo
        });
        String codigo = String.format("%06d", random.nextInt(1_000_000));
        CodigoConfirmacao c = new CodigoConfirmacao();
        c.setPedidoId(pedido.getId());
        c.setTipo(TipoCodigo.valueOf(pedido.getModalidade().name()));
        c.setCodigoHash(hmac("conf:" + pedido.getId() + ":" + codigo));
        c.setExpiraEm(LocalDateTime.now().plusHours(props.codigos().confirmacaoValidadeHoras()));
        confirmacoes.save(c);
        return codigo;
    }

    /**
     * Valida e consome o código. As tentativas incorretas são persistidas mesmo com a exceção
     * (noRollbackFor), para que o limite funcione.
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public void validarEConsumirConfirmacao(Long pedidoId, String codigo) {
        CodigoConfirmacao c = confirmacoes.findByPedidoIdAndAtivoTrue(pedidoId)
                .orElseThrow(() -> new NegocioException("Não há código de confirmação ativo. O cliente pode gerar um novo, ou use a conclusão manual."));
        if (c.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new NegocioException("Código de confirmação expirado. O cliente pode gerar um novo, ou use a conclusão manual.");
        }
        if (c.getTentativas() >= props.codigos().confirmacaoMaxTentativas()) {
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de tentativas excedido. Gere um novo código ou use a conclusão manual.");
        }
        String esperado = c.getCodigoHash();
        String informado = hmac("conf:" + pedidoId + ":" + (codigo == null ? "" : codigo.trim()));
        if (!MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), informado.getBytes(StandardCharsets.UTF_8))) {
            c.setTentativas(c.getTentativas() + 1);
            confirmacoes.save(c);
            throw new NegocioException("Código de confirmação incorreto");
        }
        c.setUtilizadoEm(LocalDateTime.now());
        c.setAtivo(false);
        confirmacoes.save(c);
    }

    private String hmac(String valor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveHmac);
            return HexFormat.of().formatHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
