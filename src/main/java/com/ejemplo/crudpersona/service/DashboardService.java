package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.repository.CategoriaRepository;
import com.ejemplo.crudpersona.repository.PersonaRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /** Limites superiores (exclusivos) de los rangos de precio; el ultimo rango queda abierto. */
    private static final int[] LIMITES_PRECIO = {10, 50, 100, 500, 1000};

    private final PersonaRepository personaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    public record SerieCategoria(String nombre, long productos, double precioPromedio) {}

    public record RangoPrecio(String etiqueta, long cantidad) {}

    public record Resumen(
            long personas,
            long categorias,
            long productos,
            BigDecimal valorCatalogo,
            BigDecimal precioPromedio,
            long personasConEmail,
            long personasConTelefono,
            long personasContactoCompleto,
            List<SerieCategoria> porCategoria,
            List<RangoPrecio> rangosPrecio,
            List<Producto> masCaros) {}

    public Resumen obtenerResumen() {
        List<BigDecimal> precios = productoRepository.listarPrecios();
        BigDecimal total = precios.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedio = precios.isEmpty()
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(precios.size()), 2, RoundingMode.HALF_UP);

        List<SerieCategoria> porCategoria = productoRepository.resumenPorCategoria().stream()
                .map(fila -> new SerieCategoria(
                        (String) fila[0],
                        ((Number) fila[1]).longValue(),
                        fila[2] == null ? 0 : ((Number) fila[2]).doubleValue()))
                .toList();

        return new Resumen(
                personaRepository.count(),
                categoriaRepository.count(),
                precios.size(),
                total,
                promedio,
                personaRepository.contarConEmail(),
                personaRepository.contarConTelefono(),
                personaRepository.contarContactoCompleto(),
                porCategoria,
                agruparPorRango(precios),
                productoRepository.top5MasCaros());
    }

    private List<RangoPrecio> agruparPorRango(List<BigDecimal> precios) {
        long[] conteo = new long[LIMITES_PRECIO.length + 1];
        for (BigDecimal precio : precios) {
            int i = 0;
            while (i < LIMITES_PRECIO.length && precio.doubleValue() >= LIMITES_PRECIO[i]) {
                i++;
            }
            conteo[i]++;
        }

        List<RangoPrecio> rangos = new ArrayList<>();
        int desde = 0;
        for (int i = 0; i < LIMITES_PRECIO.length; i++) {
            rangos.add(new RangoPrecio(desde + "–" + LIMITES_PRECIO[i], conteo[i]));
            desde = LIMITES_PRECIO[i];
        }
        rangos.add(new RangoPrecio("≥ " + desde, conteo[LIMITES_PRECIO.length]));
        return rangos;
    }
}
