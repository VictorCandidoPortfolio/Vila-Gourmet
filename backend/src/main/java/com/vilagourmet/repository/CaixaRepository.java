package com.vilagourmet.repository;

import com.vilagourmet.model.Caixa;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaixaRepository extends JpaRepository<Caixa, Long> {

    Optional<Caixa> findFirstByFechadoEmIsNull();
}
