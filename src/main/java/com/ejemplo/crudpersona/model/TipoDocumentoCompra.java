package com.ejemplo.crudpersona.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Documento que entrega el proveedor y respalda la compra. */
@Getter
@RequiredArgsConstructor
public enum TipoDocumentoCompra {
    FACTURA("Factura"),
    BOLETA("Boleta de venta"),
    GUIA_REMISION("Guia de remision"),
    NOTA_VENTA("Nota de venta");

    private final String descripcion;

    @Override
    public String toString() {
        return descripcion;
    }
}
