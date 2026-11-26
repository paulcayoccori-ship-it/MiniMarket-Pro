package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    /** Kardex del producto en orden cronologico. */
    @Query("""
            SELECT m FROM MovimientoInventario m
            WHERE m.producto.id = :productoId
            ORDER BY m.fecha, m.id
            """)
    List<MovimientoInventario> kardex(@Param("productoId") Long productoId);

    boolean existsByVentaId(Long ventaId);

    boolean existsByProductoId(Long productoId);
}
