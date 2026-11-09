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
@Table(name = "funcionario")
@Getter
@Setter
public class Funcionario extends EntidadeBase {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_funcionario_id")
    private ContaFuncionario conta;

    private String nome;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "funcionario_perfil",
            joinColumns = @JoinColumn(name = "funcionario_id"),
            inverseJoinColumns = @JoinColumn(name = "perfil_id"))
    private Set<Perfil> perfis = new HashSet<>();

    private LocalDateTime criadoEm = LocalDateTime.now();
}
