package com.vilagourmet.repository;

import com.vilagourmet.model.HistoricoEdicaoVenda;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoEdicaoVendaRepository extends JpaRepository<HistoricoEdicaoVenda, Long> {
    List<HistoricoEdicaoVenda> findByPedidoIdOrderByIdAsc(Long pedidoId);
}
