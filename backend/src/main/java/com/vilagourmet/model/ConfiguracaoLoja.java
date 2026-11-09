package com.vilagourmet.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "configuracao_loja")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoLoja {

    public static final String TAXA_ENTREGA = "TAXA_ENTREGA";

    @Id
    private String chave;

    private String valor;

    public ConfiguracaoLoja(String chave, String valor) {
        this.chave = chave;
        this.valor = valor;
    }
}
