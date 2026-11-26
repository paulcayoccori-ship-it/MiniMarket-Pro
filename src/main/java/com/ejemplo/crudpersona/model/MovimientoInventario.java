package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Linea del kardex: cada entrada o salida de stock con el saldo resultante, de modo
 * que siempre se puede reconstruir por que un producto tiene el stock que tiene.
 */
@Entity
@Table(name = "movimiento_inventario",
        indexes = {
                @Index(name = "ix_movimiento_producto", columnList = "producto_id, fecha"),
                @Index(name = "ix_movimiento_venta", columnList = "venta_id"),
                @Index(name = "ix_movimiento_compra", columnList = "compra_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "producto")
@EqualsAndHashCode(of = "id")
public class MovimientoInventario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    /** Siempre positiva; el signo lo da {@link TipoMovimiento#isEntrada()}. */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "stock_resultante", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockResultante;

    /** Costo unitario sin IGV (solo en compras). */
    @Column(name = "costo_unitario", precision = 12, scale = 4)
    private BigDecimal costoUnitario;

    /** Comprobante o documento que origina el movimiento. */
    @Column(nullable = false, length = 40)
    private String documento;

    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "compra_id")
    private Long compraId;

    public BigDecimal getEntrada() {
        return tipo.isEntrada() ? cantidad : BigDecimal.ZERO;
    }

    public BigDecimal getSalida() {
        return tipo.isEntrada() ? BigDecimal.ZERO : cantidad;
    }
}
