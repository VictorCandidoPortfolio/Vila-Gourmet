package com.vilagourmet.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpfTest {

    @Test
    void validaDigitosVerificadores() {
        assertThat(Cpf.valido("52998224725")).isTrue();
        assertThat(Cpf.valido("52998224724")).isFalse();
        assertThat(Cpf.valido("11111111111")).isFalse();
        assertThat(Cpf.valido("123")).isFalse();
        assertThat(Cpf.valido(null)).isFalse();
    }

    @Test
    void normalizaEMascara() {
        assertThat(Cpf.normalizar("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Cpf.mascarar("52998224725")).isEqualTo("***.***.***-25");
    }
}
