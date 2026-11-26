package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Linea del comprobante. Guarda una copia del codigo y nombre del producto para
 * que el comprobante impreso no cambie si el producto se edita despues.
 */
@Entity
@Table(name = "venta_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"venta", "producto"})
@EqualsAndHashCode(of = "id")
public class VentaDetalle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(nullable = false)
    private Integer item;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @NotBlank
    @Column(name = "codigo_producto", nullable = false, length = 255)
    private String codigoProducto;

    @NotBlank
    @Column(name = "nombre_producto", nullable = false, length = 255)
    private String nombreProducto;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "La cantidad debe ser mayor a cero")
    @DecimalMax(value = "99999", message = "La cantidad maxima es 99999")
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a cero")
    @Digits(integer = 8, fraction = 2)
    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    /** Cantidad x precio unitario (en la misma modalidad de IGV que la venta). */
    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;
}
