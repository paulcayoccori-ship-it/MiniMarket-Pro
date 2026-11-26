package com.ejemplo.crudpersona.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Calculo de base imponible, IGV y total de un comprobante (Peru, tasa 18%).
 * <ul>
 *   <li><b>Sin IGV</b> (inafecto/exonerado): base = total = suma de importes.</li>
 *   <li><b>Precios con IGV</b>: el total es la suma; la base se obtiene dividiendo
 *       entre 1.18 y el IGV es la diferencia (asi base + IGV cuadra al centimo).</li>
 *   <li><b>Precios sin IGV</b>: la base es la suma y el IGV se agrega encima.</li>
 * </ul>
 */
public final class CalculadoraIgv {

    public static final BigDecimal TASA_IGV = new BigDecimal("0.18");

    private CalculadoraIgv() {
    }

    public record Totales(BigDecimal baseImponible, BigDecimal igv, BigDecimal total, BigDecimal tasa) {
        public static final Totales CERO = new Totales(cero(), cero(), cero(), TASA_IGV);
    }

    public static BigDecimal importe(int cantidad, BigDecimal precioUnitario) {
        return importe(BigDecimal.valueOf(cantidad), precioUnitario);
    }

    /** Cantidad con decimales (p. ej. 0.750 kg) por precio unitario, redondeado a centimos. */
    public static BigDecimal importe(BigDecimal cantidad, BigDecimal precioUnitario) {
        return precioUnitario.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
    }

    public static Totales calcular(Collection<BigDecimal> importes, boolean aplicaIgv, boolean preciosIncluyenIgv) {
        BigDecimal suma = importes.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (!aplicaIgv) {
            return new Totales(suma, cero(), suma, BigDecimal.ZERO);
        }
        if (preciosIncluyenIgv) {
            BigDecimal base = suma.divide(BigDecimal.ONE.add(TASA_IGV), 2, RoundingMode.HALF_UP);
            return new Totales(base, suma.subtract(base), suma, TASA_IGV);
        }
        BigDecimal igv = suma.multiply(TASA_IGV).setScale(2, RoundingMode.HALF_UP);
        return new Totales(suma, igv, suma.add(igv), TASA_IGV);
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
