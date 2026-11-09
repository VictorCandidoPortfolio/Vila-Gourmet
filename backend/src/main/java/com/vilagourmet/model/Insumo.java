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
@Table(name = "insumo")
@Getter
@Setter
public class Insumo extends EntidadeBase {

    private String nome;

    private String descricao;

    private String imagemUrl;

    private BigDecimal valorReferencia;

    private boolean disponivel = true;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
