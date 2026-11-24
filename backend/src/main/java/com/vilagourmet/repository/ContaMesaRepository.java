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

public interface ContaMesaRepository extends JpaRepository<ContaMesa, Long> {

    Optional<ContaMesa> findByCodigoConta(String codigoConta);
    boolean existsByCodigoConta(String codigoConta);
    boolean existsByCpfResponsavelAndEstadoIn(String cpfResponsavel, Collection<EstadoContaMesa> estados);
    Optional<ContaMesa> findFirstByCpfResponsavelAndEstadoInOrderByAbertaEmDesc(
            String cpfResponsavel, Collection<EstadoContaMesa> estados);
    List<ContaMesa> findByEstadoOrderByAbertaEmDesc(EstadoContaMesa estado);
    List<ContaMesa> findAllByOrderByAbertaEmDesc();
}
