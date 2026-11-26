package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "producto",
        uniqueConstraints = @UniqueConstraint(name = "uk_producto_codigo", columnNames = "codigo_producto"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
    @Column(nullable = false, length = 255)
    private String nombre;

    @NotBlank(message = "El codigo de producto es obligatorio")
    @Size(max = 255)
    @Column(name = "codigo_producto", nullable = false, length = 255)
    private String codigoProducto;

    @Size(max = 255)
    @Column(length = 255)
    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener como maximo 8 enteros y 2 decimales")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    /** Precio al que se compra al proveedor (referencial, sin afectar el costo promedio). */
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio de compra no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio de compra debe tener como maximo 8 enteros y 2 decimales")
    @Column(name = "precio_compra", precision = 10, scale = 2)
    private BigDecimal precioCompra;

    /**
     * {@code true} para productos a granel o sin codigo de barras: el sistema les asigna
     * un codigo interno (GR-0001...) y se venden eligiendolos en el catalogo.
     */
    @Column(name = "granel")
    private Boolean granel;

    /** Unidad de venta (Unidad, Kilo, Six pack...). Si es null se trata como "Unidad". */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unidad_id")
    private UnidadMedida unidad;

    /** Nombre del archivo de foto subido por el usuario (carpeta imagenes-productos). Opcional. */
    @Size(max = 255)
    @Column(length = 255)
    private String imagen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** Unidades disponibles. Solo cambia con compras, ventas y anulaciones (ver InventarioService). */
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 3, columnDefinition = "numeric default 0")
    private BigDecimal stock = BigDecimal.ZERO;

    /** Por debajo de este nivel el producto aparece con alerta de reposicion. */
    @Builder.Default
    @Min(value = 0, message = "El stock minimo no puede ser negativo")
    @Max(value = 99999, message = "El stock minimo maximo es 99999")
    @Column(name = "stock_minimo", nullable = false, columnDefinition = "integer default 5")
    private Integer stockMinimo = 5;

    /** Costo unitario promedio ponderado (sin IGV), actualizado en cada compra. */
    @Column(name = "costo_promedio", precision = 12, scale = 4)
    private BigDecimal costoPromedio;

    public boolean isAGranel() {
        return Boolean.TRUE.equals(granel);
    }

    /** Abreviatura de la unidad de venta ("und" si no tiene). */
    public String getAbreviaturaUnidad() {
        return unidad == null ? UnidadMedida.ABREV_UNIDAD : unidad.getAbreviatura();
    }

    /** {@code true} si se vende por peso/volumen y admite cantidades con decimales. */
    public boolean isVentaFraccionada() {
        return unidad != null && unidad.isDecimales();
    }

    public boolean isSinStock() {
        return stock == null || stock.signum() <= 0;
    }

    public boolean isStockBajo() {
        return !isSinStock() && stock.compareTo(BigDecimal.valueOf(stockMinimo)) <= 0;
    }
}

