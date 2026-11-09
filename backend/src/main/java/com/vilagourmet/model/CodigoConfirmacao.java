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
@Table(name = "codigo_confirmacao")
@Getter
@Setter
public class CodigoConfirmacao extends EntidadeBase {

    private Long pedidoId;

    private String codigoHash;

    @Enumerated(EnumType.STRING)
    private TipoCodigo tipo;

    private int tentativas;

    private LocalDateTime expiraEm;

    private LocalDateTime utilizadoEm;

    private boolean ativo = true;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
