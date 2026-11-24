package com.vilagourmet.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vilagourmet.exception.NegocioException;
import org.junit.jupiter.api.Test;

class SenhaSeguraTest {

    @Test
    void aceitaSenhaQueCumpreTodosOsRequisitos() {
        assertThat(SenhaSegura.valida("Vila@2026")).isTrue();
    }

    @Test
    void rejeitaCadaRequisitoAusente() {
        assertThat(SenhaSegura.valida("Vila@20")).isFalse();
        assertThat(SenhaSegura.valida("vila@2026")).isFalse();
        assertThat(SenhaSegura.valida("VILA@2026")).isFalse();
        assertThat(SenhaSegura.valida("VilaGourmet@")).isFalse();
        assertThat(SenhaSegura.valida("VilaGourmet2026")).isFalse();
    }

    @Test
    void servicoRecusaSenhaInvalidaComMensagemEspecifica() {
        assertThatThrownBy(() -> SenhaSegura.exigirValida("senhafraca"))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Confira os requisitos de segurança da senha");
    }
}
