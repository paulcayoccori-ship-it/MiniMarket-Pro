package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.UnidadMedida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Long> {
    Optional<UnidadMedida> findByNombreIgnoreCase(String nombre);

    Optional<UnidadMedida> findFirstByAbreviaturaIgnoreCase(String abreviatura);
}
