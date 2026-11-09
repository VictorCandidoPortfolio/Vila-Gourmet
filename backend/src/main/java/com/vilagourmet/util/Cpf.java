package com.vilagourmet.util;

import com.vilagourmet.exception.NegocioException;

/** Utilitários de CPF: normalização, validação dos dígitos verificadores e máscara. */
public final class Cpf {

    private Cpf() {
    }

    public static String normalizar(String cpf) {
        return cpf == null ? null : cpf.replaceAll("\\D", "");
    }

    /** Normaliza e valida; lança NegocioException se inválido. */
    public static String exigirValido(String cpf) {
        String n = normalizar(cpf);
        if (!valido(n)) {
            throw new NegocioException("CPF inválido");
        }
        return n;
    }

    public static boolean valido(String n) {
        if (n == null || n.length() != 11 || n.chars().distinct().count() == 1) {
            return false;
        }
        return digito(n, 9) == n.charAt(9) - '0' && digito(n, 10) == n.charAt(10) - '0';
    }

    private static int digito(String n, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (n.charAt(i) - '0') * (tamanho + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    /** Exibe apenas os dois últimos dígitos: ***.***.***-XX */
    public static String mascarar(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return null;
        }
        return "***.***.***-" + cpf.substring(9);
    }
}
