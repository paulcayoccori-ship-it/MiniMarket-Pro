package com.ejemplo.crudpersona.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Tipos de comprobante que emite el punto de venta, cada uno con su serie. */
@Getter
@RequiredArgsConstructor
public enum TipoComprobante {
    BOLETA("B001", "Boleta de venta electrónica"),
    NOTA_VENTA("NV01", "Nota de venta");

    private final String serie;
    private final String descripcion;

    @Override
    public String toString() {
        return descripcion;
    }
}
