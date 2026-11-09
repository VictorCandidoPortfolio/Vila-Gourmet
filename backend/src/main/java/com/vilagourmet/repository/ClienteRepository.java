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

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByContaId(Long contaId);

    Optional<Cliente> findByCpf(String cpf);

    boolean existsByCpf(String cpf);
}
