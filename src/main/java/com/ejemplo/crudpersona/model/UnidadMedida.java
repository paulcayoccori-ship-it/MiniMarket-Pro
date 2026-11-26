package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Unidad en la que se vende un producto: Unidad, Kilo, Six pack, Caja...
 * Las que permiten decimales (p. ej. Kilo) aceptan cantidades como 0.750.
 */
@Entity
@Table(name = "unidad_medida",
        uniqueConstraints = @UniqueConstraint(name = "uk_unidad_nombre", columnNames = "nombre"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class UnidadMedida {
    public static final String ABREV_UNIDAD = "und";
    public static final String ABREV_KILO = "kg";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre de la unidad es obligatorio")
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String nombre;

    /** Texto corto que se muestra junto a precios y cantidades: und, kg, six. */
    @NotBlank(message = "La abreviatura es obligatoria")
    @Size(max = 10)
    @Column(nullable = false, length = 10)
    private String abreviatura;

    /** {@code true} si se puede vender una fraccion (peso o volumen). */
    @Column(name = "decimales", nullable = false)
    private boolean decimales;

    @Override
    public String toString() {
        return nombre + " (" + abreviatura + ")";
    }
}
