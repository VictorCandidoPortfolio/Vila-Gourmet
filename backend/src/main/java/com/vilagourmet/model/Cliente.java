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
@Table(name = "cliente")
@Getter
@Setter
public class Cliente extends EntidadeBase {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_cliente_id")
    private ContaCliente conta;

    private String nome;

    private String cpf;

    private LocalDateTime criadoEm = LocalDateTime.now();
}
