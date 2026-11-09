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
@Table(name = "configuracao_precificacao")
@Getter
@Setter
public class ConfiguracaoPrecificacao {

    @Id
    @Column(name = "produto_id")
    private Long produtoId;

    @Enumerated(EnumType.STRING)
    private EstrategiaPrecificacao estrategia;

    private boolean participaPrecoBase;
}
