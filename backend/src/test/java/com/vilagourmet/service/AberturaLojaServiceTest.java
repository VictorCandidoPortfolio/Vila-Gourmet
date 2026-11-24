package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vilagourmet.exception.NegocioException;
import com.vilagourmet.model.Caixa;
import com.vilagourmet.repository.CaixaRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AberturaLojaServiceTest {

    private final CaixaRepository caixas = mock(CaixaRepository.class);
    private final AberturaLojaService abertura = new AberturaLojaService(caixas);

    @Test
    void caixaAbertoSemOpcaoMarcadaNaoLiberaVendasPeloSite() {
        Caixa caixa = new Caixa();
        caixa.setVendasOnline(false);
        when(caixas.findFirstByFechadoEmIsNull()).thenReturn(Optional.of(caixa));

        assertThat(abertura.aberta()).isTrue();
        assertThat(abertura.vendasOnlineDisponiveis()).isFalse();
        assertThatThrownBy(abertura::exigirVendasOnline)
                .isInstanceOf(NegocioException.class)
                .hasMessage("As vendas pelo site estão indisponíveis no momento.");
    }

    @Test
    void caixaAbertoComOpcaoMarcadaLiberaVendasPeloSite() {
        Caixa caixa = new Caixa();
        caixa.setVendasOnline(true);
        when(caixas.findFirstByFechadoEmIsNull()).thenReturn(Optional.of(caixa));

        assertThat(abertura.vendasOnlineDisponiveis()).isTrue();
    }
}
