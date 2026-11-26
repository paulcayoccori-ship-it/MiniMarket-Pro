package com.ejemplo.crudpersona.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Forma en que el cliente paga la venta. */
@Getter
@RequiredArgsConstructor
public enum MetodoPago {
    EFECTIVO("Efectivo"),
    TARJETA("Tarjeta"),
    YAPE("Yape");

    private final String descripcion;

    @Override
    public String toString() {
        return descripcion;
    }
}
