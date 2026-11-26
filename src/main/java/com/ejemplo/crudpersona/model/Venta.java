package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cabecera de un comprobante de venta. Los importes se guardan ya calculados
 * (base imponible, IGV y total) para que el comprobante no cambie si luego se
 * modifican los precios del catalogo.
 */
@Entity
@Table(name = "venta",
        uniqueConstraints = @UniqueConstraint(name = "uk_venta_comprobante", columnNames = "comprobante"),
        indexes = @Index(name = "ix_venta_fecha", columnList = "fecha"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"cliente", "detalles"})
@EqualsAndHashCode(of = "id")
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_comprobante", nullable = false, length = 20)
    private TipoComprobante tipoComprobante;

    @NotBlank
    @Column(nullable = false, length = 4)
    private String serie;

    @NotNull
    @Column(nullable = false)
    private Integer numero;

    /** Serie y numero formateados, p. ej. {@code B001-00000125}. */
    @NotBlank
    @Column(nullable = false, length = 20)
    private String comprobante;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fecha;

    @NotNull(message = "Selecciona un cliente")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Persona cliente;

    /** Si es {@code false} la venta esta inafecta/exonerada: el IGV es cero. */
    @Column(name = "aplica_igv", nullable = false)
    private boolean aplicaIgv;

    /** Indica si los precios unitarios del detalle ya incluyen el IGV. */
    @Column(name = "precios_incluyen_igv", nullable = false)
    private boolean preciosIncluyenIgv;

    @NotNull
    @Column(name = "tasa_igv", nullable = false, precision = 5, scale = 4)
    private BigDecimal tasaIgv;

    @NotNull
    @Column(name = "base_imponible", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoVenta estado;

    @Size(max = 255)
    @Column(length = 255)
    private String observacion;

    /** Forma de pago elegida en la pantalla de cobro (null en ventas antiguas). */
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 15)
    private MetodoPago metodoPago;

    /** Importe entregado por el cliente (solo efectivo) y vuelto entregado. */
    @Column(name = "monto_recibido", precision = 12, scale = 2)
    private BigDecimal montoRecibido;

    @Column(precision = 12, scale = 2)
    private BigDecimal vuelto;

    @Valid
    @NotEmpty(message = "Agrega al menos un producto al carrito")
    @Builder.Default
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("item ASC")
    private List<VentaDetalle> detalles = new ArrayList<>();

    public void agregarDetalle(VentaDetalle detalle) {
        detalle.setVenta(this);
        detalle.setItem(detalles.size() + 1);
        detalles.add(detalle);
    }

    public boolean isAnulada() {
        return estado == EstadoVenta.ANULADA;
    }

    public static String formatearComprobante(String serie, int numero) {
        return serie + "-" + String.format("%08d", numero);
    }
}
