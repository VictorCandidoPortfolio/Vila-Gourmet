package com.vilagourmet.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Textos {

    private Textos() {
    }

    /** Minúsculas, sem acentos e com espaços simples: base para comparar e pesquisar nomes. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
