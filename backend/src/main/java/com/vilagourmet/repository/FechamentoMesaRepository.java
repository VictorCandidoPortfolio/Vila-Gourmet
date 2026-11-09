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

public interface FechamentoMesaRepository extends JpaRepository<FechamentoMesa, Long> {

    List<FechamentoMesa> findByContaMesaIdOrderByIdDesc(Long contaMesaId);
    Optional<FechamentoMesa> findByContaMesaIdAndEstado(Long contaMesaId, EstadoFechamento estado);
}
