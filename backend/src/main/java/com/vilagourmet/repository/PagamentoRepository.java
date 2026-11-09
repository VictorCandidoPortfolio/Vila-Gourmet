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

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    List<Pagamento> findByPedidoIdOrderByIdDesc(Long pedidoId);
    List<Pagamento> findByFechamentoMesaIdOrderByIdDesc(Long fechamentoMesaId);
    Optional<Pagamento> findByPedidoIdAndEstado(Long pedidoId, EstadoPagamento estado);
    Optional<Pagamento> findByFechamentoMesaIdAndEstado(Long fechamentoMesaId, EstadoPagamento estado);
    @Query("select coalesce(sum(p.valor), 0) from Pagamento p where p.estado = :estado and p.confirmadoEm >= :ini and p.confirmadoEm < :fim")
    BigDecimal totalPorEstado(@Param("estado") EstadoPagamento estado, @Param("ini") LocalDateTime ini, @Param("fim") LocalDateTime fim);
}
