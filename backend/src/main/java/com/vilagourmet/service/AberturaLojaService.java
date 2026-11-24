package com.vilagourmet.service;

import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Caixa;
import com.vilagourmet.repository.CaixaRepository;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estado operacional do caixa e disponibilidade independente da loja pública.
 */
@Service
public class AberturaLojaService {

    private final CaixaRepository caixas;

    public AberturaLojaService(CaixaRepository caixas) {
        this.caixas = caixas;
    }

    @Transactional(readOnly = true)
    public Optional<Caixa> caixaAberto() {
        return caixas.findFirstByFechadoEmIsNull();
    }

    @Transactional(readOnly = true)
    public boolean aberta() {
        return caixaAberto().isPresent();
    }

    @Transactional(readOnly = true)
    public boolean vendasOnlineDisponiveis() {
        return caixaAberto().map(Caixa::isVendasOnline).orElse(false);
    }

    @Transactional(readOnly = true)
    public void exigirCaixaAberto() {
        if (!aberta()) {
            throw new NegocioException(HttpStatus.CONFLICT, "A pizzaria está fechada no momento. Não é possível realizar vendas.");
        }
    }

    @Transactional(readOnly = true)
    public void exigirVendasOnline() {
        if (!vendasOnlineDisponiveis()) {
            throw new NegocioException(HttpStatus.CONFLICT, "As vendas pelo site estão indisponíveis no momento.");
        }
    }
}
