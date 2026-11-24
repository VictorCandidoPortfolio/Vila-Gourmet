package com.vilagourmet.util;

import com.vilagourmet.exception.NegocioException;
import java.util.regex.Pattern;

/** Política única para toda senha nova de cliente ou funcionário. */
public final class SenhaSegura {

    public static final String REGEX = "^(?=.{8,72}$)(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).*$";
    public static final String MENSAGEM = "deve ter de 8 a 72 caracteres, com letra maiúscula, letra minúscula, número e caractere especial";
    private static final Pattern PADRAO = Pattern.compile(REGEX);

    private SenhaSegura() {
    }

    public static boolean valida(String senha) {
        return senha != null && PADRAO.matcher(senha).matches();
    }

    public static void exigirValida(String senha) {
        if (!valida(senha)) {
            throw new NegocioException("Confira os requisitos de segurança da senha");
        }
    }
}
