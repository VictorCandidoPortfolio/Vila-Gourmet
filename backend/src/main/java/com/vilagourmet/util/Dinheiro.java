package com.vilagourmet.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Aritmética monetária com BigDecimal: arredondamento final HALF_UP em duas casas. */
public final class Dinheiro {

    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Dinheiro() {
    }

    public static BigDecimal arredondar(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
