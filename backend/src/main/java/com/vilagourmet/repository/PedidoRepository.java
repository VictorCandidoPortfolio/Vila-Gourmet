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

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    Optional<Pedido> findByNumeroPedido(String numeroPedido);
    boolean existsByNumeroPedido(String numeroPedido);
    Optional<Pedido> findByChaveIdempotencia(String chaveIdempotencia);
    List<Pedido> findByClienteIdOrderByCriadoEmDesc(Long clienteId);
    List<Pedido> findByContaMesaIdOrderByIdAsc(Long contaMesaId);
    @Query("select p from Pedido p where p.estado in :estados and (:modalidade is null or p.modalidade = :modalidade) "
            + "and (:ini is null or p.criadoEm >= :ini) and (:fim is null or p.criadoEm < :fim) order by p.criadoEm desc")
    Page<Pedido> buscar(@Param("estados") Collection<EstadoPedido> estados, @Param("modalidade") Modalidade modalidade,
                        @Param("ini") LocalDateTime ini, @Param("fim") LocalDateTime fim, Pageable pageable);
    List<Pedido> findByEstadoNotIn(Collection<EstadoPedido> estados);
    @Query("select count(p) from Pedido p where p.criadoEm >= :ini and p.criadoEm < :fim")
    long contar(@Param("ini") LocalDateTime ini, @Param("fim") LocalDateTime fim);
    @Query("select p.estado, count(p) from Pedido p where p.criadoEm >= :ini and p.criadoEm < :fim group by p.estado")
    List<Object[]> contarPorEstado(@Param("ini") LocalDateTime ini, @Param("fim") LocalDateTime fim);
    @Query("select p.modalidade, count(p) from Pedido p where p.criadoEm >= :ini and p.criadoEm < :fim group by p.modalidade")
    List<Object[]> contarPorModalidade(@Param("ini") LocalDateTime ini, @Param("fim") LocalDateTime fim);
    @Query("select coalesce(sum(p.total), 0) from Pedido p where p.criadoEm >= :ini and p.criadoEm < :fim and p.estado not in :excluidos")
    BigDecimal totalVendido(@Param("ini") LocalDateTime ini, @Param("fim") LocalDateTime fim, @Param("excluidos") Collection<EstadoPedido> excluidos);
}
