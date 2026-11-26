package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    Optional<Producto> findByCodigoProducto(String codigoProducto);

    boolean existsByCodigoProducto(String codigoProducto);

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            """)
    Page<Producto> findAllConCategoria(Pageable pageable);

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            WHERE LOWER(p.nombre)          LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(p.codigoProducto)  LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(p.descripcion)     LIKE LOWER(CONCAT('%', :texto, '%'))
            """)
    Page<Producto> buscar(@Param("texto") String texto, Pageable pageable);

    /** Inventario con filtro de texto y de estado de stock (TODOS, BAJO, SIN_STOCK, DISPONIBLE). */
    @Query(value = """
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            WHERE (LOWER(p.nombre)         LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(p.codigoProducto) LIKE LOWER(CONCAT('%', :texto, '%')))
              AND (:estado = 'TODOS'
                OR (:estado = 'SIN_STOCK'  AND p.stock <= 0)
                OR (:estado = 'BAJO'       AND p.stock > 0 AND p.stock <= p.stockMinimo)
                OR (:estado = 'DISPONIBLE' AND p.stock > p.stockMinimo))
            """,
            countQuery = """
            SELECT COUNT(p) FROM Producto p
            WHERE (LOWER(p.nombre)         LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(p.codigoProducto) LIKE LOWER(CONCAT('%', :texto, '%')))
              AND (:estado = 'TODOS'
                OR (:estado = 'SIN_STOCK'  AND p.stock <= 0)
                OR (:estado = 'BAJO'       AND p.stock > 0 AND p.stock <= p.stockMinimo)
                OR (:estado = 'DISPONIBLE' AND p.stock > p.stockMinimo))
            """)
    Page<Producto> inventario(@Param("texto") String texto, @Param("estado") String estado, Pageable pageable);

    @Query("SELECT p FROM Producto p LEFT JOIN FETCH p.categoria ORDER BY p.codigoProducto")
    List<Producto> listarConCategoria();

    // ---- Estadisticas para el dashboard ----

    @Query("""
            SELECT COALESCE(c.nombre, 'Sin categoria'), COUNT(p), AVG(p.precio)
            FROM Producto p
            LEFT JOIN p.categoria c
            GROUP BY c.id, c.nombre
            ORDER BY COUNT(p) DESC
            """)
    List<Object[]> resumenPorCategoria();

    @Query("SELECT p.precio FROM Producto p")
    List<BigDecimal> listarPrecios();

    @Query("SELECT p FROM Producto p LEFT JOIN FETCH p.categoria ORDER BY p.precio DESC LIMIT 5")
    List<Producto> top5MasCaros();
}

