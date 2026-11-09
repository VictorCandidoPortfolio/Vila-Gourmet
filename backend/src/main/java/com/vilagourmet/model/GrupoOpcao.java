package com.vilagourmet.model;

import com.vilagourmet.model.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "grupo_opcao")
@Getter
@Setter
public class GrupoOpcao extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private String nome;

    private int minimo;

    private int maximo;

    @Enumerated(EnumType.STRING)
    private ModalidadeCobranca modalidadeCobranca;

    private boolean ativo = true;
}
