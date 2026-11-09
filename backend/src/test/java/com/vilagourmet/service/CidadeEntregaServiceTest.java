package com.vilagourmet.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vilagourmet.model.AreaEntrega;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regras de não duplicação da importação de bairros. */
class CidadeEntregaServiceTest {

    private static AreaEntrega area(String bairro, Integer ibge) {
        AreaEntrega a = new AreaEntrega();
        a.setBairro(bairro);
        a.setCidade("Piracicaba");
        a.setCidadeIbgeId(ibge);
        return a;
    }

    @Test
    void primeiraImportacaoCriaTudo() {
        CidadeEntregaService.Plano p = CidadeEntregaService.planejar(List.of(), List.of("Centro", "Alemães"));
        assertThat(p.paraCriar()).containsExactly("Centro", "Alemães");
        assertThat(p.paraVincular()).isEmpty();
    }

    @Test
    void importacaoRepetidaNaoCriaNada() {
        List<AreaEntrega> ja = List.of(area("Centro", 3538709), area("Alemães", 3538709));
        CidadeEntregaService.Plano p = CidadeEntregaService.planejar(ja, List.of("Centro", "Alemães"));
        assertThat(p.paraCriar()).isEmpty();
        assertThat(p.paraVincular()).isEmpty();
    }

    @Test
    void comparaSemAcentoEMaiusculasEPreservaOQueOGestorJaDesativou() {
        AreaEntrega desativada = area("alemaes", 3538709);
        desativada.setAtiva(false);
        CidadeEntregaService.Plano p = CidadeEntregaService.planejar(List.of(desativada), List.of("Alemães", "Paulista"));
        assertThat(p.paraCriar()).containsExactly("Paulista");
        assertThat(desativada.isAtiva()).isFalse();
    }

    @Test
    void cadastroManualAntigoDaMesmaCidadeEhVinculadoEnaoDuplicado() {
        AreaEntrega manual = area("Centro", null);
        CidadeEntregaService.Plano p = CidadeEntregaService.planejar(List.of(manual), List.of("Centro", "Paulista"));
        assertThat(p.paraCriar()).containsExactly("Paulista");
        assertThat(p.paraVincular()).containsExactly(manual);
    }
}
