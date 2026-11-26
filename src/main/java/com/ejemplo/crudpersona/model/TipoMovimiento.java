package com.ejemplo.crudpersona.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Motivo de un movimiento de inventario y si suma (entrada) o resta (salida) stock. */
@Getter
@RequiredArgsConstructor
public enum TipoMovimiento {
    COMPRA("Compra", true),
    INVENTARIO_INICIAL("Stock inicial", true),
    ANULACION_VENTA("Anulacion de venta", true),
    VENTA("Venta", false),
    ANULACION_COMPRA("Anulacion de compra", false);

    private final String descripcion;
    private final boolean entrada;

    @Override
    public String toString() {
        return descripcion;
    }
}
