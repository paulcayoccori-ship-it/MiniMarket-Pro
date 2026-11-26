package com.ejemplo.crudpersona.seeder;

import com.ejemplo.crudpersona.model.Persona;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.TipoComprobante;
import com.ejemplo.crudpersona.model.Venta;
import com.ejemplo.crudpersona.repository.PersonaRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.repository.VentaRepository;
import com.ejemplo.crudpersona.service.VentaService;
import com.ejemplo.crudpersona.service.VentaService.LineaCarrito;
import com.ejemplo.crudpersona.service.VentaService.NuevaVenta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Registra 250 ventas repartidas en los ultimos 120 dias, con entre 1 y 5 productos
 * cada una, mas movimiento los fines de semana y algunas ventas anuladas. Usa
 * {@link VentaService} para que correlativos, base imponible, IGV y stock se calculen
 * igual que en una venta real. Cuando un producto no alcanza, registra antes una
 * compra de reposicion (fechada justo antes de la venta), asi el kardex queda
 * cronologicamente coherente. Debe ejecutarse despues del inventario inicial.
 * <p>
 * Es idempotente: si ya existen ventas no registra ninguna.
 */
@Component
@RequiredArgsConstructor
public class VentaSeeder {

    private static final int CANTIDAD = 250;
    private static final int DIAS_HISTORIA = 120;
    private static final long SEMILLA = 2026L;
    private static final BigDecimal PRECIO_ECONOMICO = new BigDecimal("100");

    private final VentaService ventaService;
    private final ComprasSimuladas compras;
    private final VentaRepository ventaRepository;
    private final PersonaRepository personaRepository;
    private final ProductoRepository productoRepository;

    @Transactional
    public int sembrar() {
        if (ventaRepository.count() > 0) {
            return 0;
        }
        List<Persona> clientes = personaRepository.findAll();
        List<Producto> productos = productoRepository.findAll();
        if (clientes.isEmpty() || productos.isEmpty()) {
            return 0;
        }
        // Los productos de consumo diario se venden mucho mas que los electrodomesticos.
        List<Producto> economicos = productos.stream()
                .filter(p -> p.getPrecio().compareTo(PRECIO_ECONOMICO) < 0)
                .toList();

        Random azar = new Random(SEMILLA);
        LocalDate hoy = LocalDate.now();

        // Primero se generan las fechas y se ordenan: asi los correlativos siguen el orden cronologico.
        List<LocalDateTime> fechas = new ArrayList<>();
        while (fechas.size() < CANTIDAD) {
            LocalDate dia = hoy.minusDays(azar.nextInt(DIAS_HISTORIA));
            boolean finDeSemana = dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (!finDeSemana && azar.nextDouble() < 0.35) {
                continue;
            }
            LocalDateTime momento = dia.atTime(8 + azar.nextInt(13), azar.nextInt(60), azar.nextInt(60));
            if (momento.isAfter(LocalDateTime.now())) {
                momento = LocalDateTime.now().minusMinutes(1 + azar.nextInt(90));
            }
            fechas.add(momento);
        }
        fechas.sort(Comparator.naturalOrder());

        int registradas = 0;
        LocalDateTime anterior = fechas.get(0).minusDays(1);
        for (LocalDateTime fecha : fechas) {
            boolean esBoleta = azar.nextDouble() < 0.85;
            boolean aplicaIgv = esBoleta || azar.nextDouble() < 0.5;
            boolean incluyeIgv = !aplicaIgv || azar.nextDouble() < 0.9;

            List<LineaCarrito> lineas = new ArrayList<>();
            Set<Long> usados = new HashSet<>();
            int items = 1 + azar.nextInt(5);
            while (lineas.size() < items) {
                List<Producto> fuente = azar.nextDouble() < 0.85 && !economicos.isEmpty() ? economicos : productos;
                Producto p = fuente.get(azar.nextInt(fuente.size()));
                if (!usados.add(p.getId())) {
                    continue;
                }
                boolean caro = p.getPrecio().compareTo(PRECIO_ECONOMICO) >= 0;
                int cantidad = caro ? 1 : 1 + azar.nextInt(azar.nextDouble() < 0.7 ? 3 : 8);
                lineas.add(new LineaCarrito(p.getId(), cantidad, p.getPrecio()));
            }

            reponerSiFalta(lineas, productos, anterior, fecha, azar);
            anterior = fecha;

            Venta venta = ventaService.registrar(new NuevaVenta(
                    esBoleta ? TipoComprobante.BOLETA : TipoComprobante.NOTA_VENTA,
                    clientes.get(azar.nextInt(clientes.size())).getId(),
                    aplicaIgv, incluyeIgv,
                    azar.nextDouble() < 0.1 ? "Entrega a domicilio" : null,
                    lineas), fecha);
            registradas++;

            if (azar.nextDouble() < 0.04) {
                ventaService.anular(venta.getId(), fecha.plusSeconds(1));
            }
        }
        return registradas;
    }

    /** Compra de reposicion (entre la venta anterior y esta) para las lineas sin stock suficiente. */
    private void reponerSiFalta(List<LineaCarrito> lineas, List<Producto> productos,
                                LocalDateTime anterior, LocalDateTime fecha, Random azar) {
        Map<Producto, Integer> faltantes = new LinkedHashMap<>();
        for (LineaCarrito l : lineas) {
            Producto p = productos.stream().filter(x -> x.getId().equals(l.productoId())).findFirst().orElseThrow();
            if (p.getStock().compareTo(l.cantidad()) < 0) {
                faltantes.put(p, ComprasSimuladas.cantidadReposicion(p, azar) + l.cantidad().intValue());
            }
        }
        if (!faltantes.isEmpty()) {
            LocalDateTime momento = fecha.minusHours(2);
            compras.comprar(faltantes, momento.isAfter(anterior) ? momento : anterior.plusMinutes(1), azar);
        }
    }
}
