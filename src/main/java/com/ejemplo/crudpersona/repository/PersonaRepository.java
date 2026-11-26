package com.ejemplo.crudpersona.repository;
import com.ejemplo.crudpersona.model.Persona;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, Long>, JpaSpecificationExecutor<Persona> {

    Optional<Persona> findByDni(String dni);

    boolean existsByDni(String dni);

    @Query("""
            SELECT p FROM Persona p
            WHERE LOWER(p.nombre)   LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(p.apellido) LIKE LOWER(CONCAT('%', :texto, '%'))
               OR p.dni             LIKE CONCAT('%', :texto, '%')
            """)
    Page<Persona> buscar(@Param("texto") String texto, Pageable pageable);

    @Query("SELECT COUNT(p) FROM Persona p WHERE p.email IS NOT NULL AND p.email <> ''")
    long contarConEmail();

    @Query("SELECT COUNT(p) FROM Persona p WHERE p.telefono IS NOT NULL AND p.telefono <> ''")
    long contarConTelefono();

    @Query("""
            SELECT COUNT(p) FROM Persona p
            WHERE p.email IS NOT NULL AND p.email <> ''
              AND p.telefono IS NOT NULL AND p.telefono <> ''
            """)
    long contarContactoCompleto();
}

