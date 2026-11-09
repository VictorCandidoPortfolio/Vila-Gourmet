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
@Table(name = "pizza_salva")
@Getter
@Setter
public class PizzaSalva extends EntidadeBase {

    private Long clienteId;

    private Long produtoId;

    private String nome;

    private String selecaoJson;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
