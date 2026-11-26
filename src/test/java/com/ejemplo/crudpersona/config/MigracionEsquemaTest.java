package com.ejemplo.crudpersona.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigracionEsquemaTest {

    @Test
    void quitaSoloLasRestriccionesObsoletas() {
        String detalle = "create table venta_detalle (id integer, cantidad integer not null check ((cantidad<=9999) and (cantidad>=1)), "
                + "importe numeric(12,2) not null, primary key (id))";
        assertThat(MigracionEsquema.quitarChecksObsoletos(detalle))
                .isEqualTo("create table venta_detalle (id integer, cantidad integer not null, "
                        + "importe numeric(12,2) not null, primary key (id))");

        String movimiento = "create table movimiento_inventario (id integer, cantidad integer not null, "
                + "tipo varchar(20) not null check (tipo in ('COMPRA','ANULACION_VENTA','VENTA','ANULACION_COMPRA')), primary key (id))";
        assertThat(MigracionEsquema.quitarChecksObsoletos(movimiento))
                .doesNotContainIgnoringCase("check")
                .contains("tipo varchar(20) not null,");

        String venta = "create table venta (estado varchar(10) not null check (estado in ('EMITIDA','ANULADA')))";
        assertThat(MigracionEsquema.quitarChecksObsoletos(venta)).isEqualTo(venta);
    }
}
