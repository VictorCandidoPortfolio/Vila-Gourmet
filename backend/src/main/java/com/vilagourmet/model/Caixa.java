package com.vilagourmet.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Uma sessão de caixa: do momento em que um funcionário abre até o fechamento. */
@Entity
@Table(name = "caixa")
@Getter
@Setter
public class Caixa extends EntidadeBase {

    private LocalDateTime abertoEm = LocalDateTime.now();

    private Long abertoPor;

    private LocalDateTime fechadoEm;

    private Long fechadoPor;

    /** Pedidos não concluídos que foram cancelados no fechamento. */
    private int pedidosCancelados;
}
