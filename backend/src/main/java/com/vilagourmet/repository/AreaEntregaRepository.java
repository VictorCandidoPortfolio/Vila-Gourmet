package com.vilagourmet.repository;

import com.vilagourmet.model.*;
import com.vilagourmet.model.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AreaEntregaRepository extends JpaRepository<AreaEntrega, Long> {

    List<AreaEntrega> findAllByOrderByCidadeAscBairroAsc();
    Optional<AreaEntrega> findByBairroIgnoreCaseAndCidadeIgnoreCaseAndCidadeIbgeId(String bairro, String cidade, Integer cidadeIbgeId);
    List<AreaEntrega> findByCidadeIgnoreCase(String cidade);

    /** Quando a UF é informada, áreas sem UF (cadastro manual antigo) continuam valendo para qualquer UF. */
    @Query("select count(a) > 0 from AreaEntrega a where lower(a.bairro) = lower(:bairro) and lower(a.cidade) = lower(:cidade) "
            + "and a.ativa = true and (:uf is null or a.uf is null or upper(a.uf) = upper(:uf))")
    boolean atendida(@Param("bairro") String bairro, @Param("cidade") String cidade, @Param("uf") String uf);
}
