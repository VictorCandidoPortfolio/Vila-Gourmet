package com.vilagourmet.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Valida a configuração na inicialização. Não cria usuários: o primeiro administrador é criado
 * pela própria tela de login (primeiro acesso), sem senha padrão.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private final AppProperties props;

    public DataInitializer(AppProperties props) {
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.jwt().secret() == null || props.jwt().secret().length() < 32) {
            throw new IllegalStateException("app.jwt.secret deve ter ao menos 32 caracteres");
        }
    }
}
