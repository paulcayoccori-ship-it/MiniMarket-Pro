package com.ejemplo.crudpersona.seeder;

import com.ejemplo.crudpersona.model.Compra;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.Proveedor;
import com.ejemplo.crudpersona.model.TipoDocumentoCompra;
import com.ejemplo.crudpersona.repository.ProveedorRepository;
import com.ejemplo.crudpersona.service.CompraService;
import com.ejemplo.crudpersona.service.CompraService.LineaCompra;
import com.ejemplo.crudpersona.service.CompraService.NuevaCompra;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Genera compras de reposicion realistas para los seeders: factura con IGV agregado,
 * costo entre 60% y 75% del precio de venta sin IGV y numeracion correlativa por proveedor.
 */
@Component
@RequiredArgsConstructor
class ComprasSimuladas {

    static final BigDecimal PRECIO_ECONOMICO = new BigDecimal("100");

    private final CompraService compraService;
    private final ProveedorRepository proveedorRepository;
    private final Map<String, Integer> correlativos = new HashMap<>();

    /** Cantidad de reposicion tipica: productos baratos por cajas, caros por pocas unidades. */
    static int cantidadReposicion(Producto p, Random azar) {
        return p.getPrecio().compareTo(PRECIO_ECONOMICO) < 0 ? 24 + azar.nextInt(37) : 3 + azar.nextInt(6);
    }

    Compra comprar(Map<Producto, Integer> cantidades, LocalDateTime fecha, Random azar) {
        List<Proveedor> proveedores = proveedorRepository.findAll();
        Proveedor proveedor = proveedores.get(azar.nextInt(proveedores.size()));
        int correlativo = correlativos.merge(proveedor.getRuc(), 100 + azar.nextInt(400), (a, b) -> a + 1 + azar.nextInt(20));

        List<LineaCompra> lineas = cantidades.entrySet().stream()
                .map(e -> new LineaCompra(e.getKey().getId(), e.getValue(), costo(e.getKey(), azar)))
                .toList();
        return compraService.registrar(new NuevaCompra(
                proveedor.getRuc(), proveedor.getRazonSocial(),
                TipoDocumentoCompra.FACTURA,
                "F" + String.format("%03d", 1 + proveedor.getRuc().charAt(10) % 3) + "-" + String.format("%06d", correlativo),
                fecha.toLocalDate(), true, false, null, lineas), fecha);
    }

    /** Costo sin IGV = precio de venta / 1.18 x margen (60% a 75%). */
    private static BigDecimal costo(Producto p, Random azar) {
        double margen = 0.60 + azar.nextDouble() * 0.15;
        return p.getPrecio().divide(new BigDecimal("1.18"), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(margen)).setScale(2, RoundingMode.HALF_UP)
                .max(new BigDecimal("0.10"));
    }
}
