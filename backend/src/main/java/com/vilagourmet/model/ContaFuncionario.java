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
@Table(name = "conta_funcionario")
@Getter
@Setter
public class ContaFuncionario extends EntidadeBase {

    private String email;

    private String senhaHash;

    private boolean ativa = true;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
