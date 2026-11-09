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
@Table(name = "area_entrega")
@Getter
@Setter
public class AreaEntrega extends EntidadeBase {

    private String bairro;

    private String cidade;

    private boolean ativa = true;

    /** Código do município no IBGE; nulo em áreas cadastradas sem vínculo com um município. */
    private Integer cidadeIbgeId;

    private String uf;

    /** MANUAL ou IBGE_OSM (bairros importados do OpenStreetMap para um município do IBGE). */
    private String origem = ORIGEM_MANUAL;

    public static final String ORIGEM_MANUAL = "MANUAL";
    public static final String ORIGEM_IMPORTADA = "IBGE_OSM";
}
