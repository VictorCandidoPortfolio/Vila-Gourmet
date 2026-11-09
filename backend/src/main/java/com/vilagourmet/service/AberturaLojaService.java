package com.vilagourmet.service;

import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Caixa;
import com.vilagourmet.repository.CaixaRepository;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pergunta única: a pizzaria está aberta? Só há vendas (loja, mesa e frente de caixa) com um caixa aberto.
 * Fica separado do {@link CaixaService} para que pedidos e mesas possam consultá-lo sem dependência circular.
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
    public void exigirAberta() {
        if (!aberta()) {
            throw new NegocioException(HttpStatus.CONFLICT, "A pizzaria está fechada no momento. Não é possível realizar vendas.");
        }
    }
}
