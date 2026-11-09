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
@Table(name = "codigo_acompanhamento")
@Getter
@Setter
public class CodigoAcompanhamento extends EntidadeBase {

    private Long pedidoId;

    private String codigoHash;

    private boolean ativo = true;

    private LocalDateTime expiraEm;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
