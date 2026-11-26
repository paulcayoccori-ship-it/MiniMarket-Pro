package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.MovimientoInventario;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.TipoMovimiento;
import com.ejemplo.crudpersona.repository.MovimientoInventarioRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unico punto que modifica el stock. Cada entrada o salida actualiza el producto y
 * deja un {@link MovimientoInventario} con el saldo resultante (kardex).
 * <ul>
 *   <li>Compra: suma stock y recalcula el costo promedio ponderado.</li>
 *   <li>Venta: resta stock; no permite quedar en negativo.</li>
 *   <li>Anulaciones: revierten el movimiento original.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventarioService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    @Getter
    @RequiredArgsConstructor
    public enum FiltroStock {
        TODOS("Todos los productos"),
        DISPONIBLE("Stock suficiente"),
        BAJO("Stock bajo"),
        SIN_STOCK("Sin stock");

        private final String etiqueta;

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    public record ResumenInventario(long productos, BigDecimal unidades, BigDecimal valorCosto,
                                    BigDecimal valorVenta, long stockBajo, long sinStock) {}

    // ------------------------------------------------------------ consultas

    public Page<Producto> listar(String texto, FiltroStock filtro, int pagina, int tamanio) {
        return productoRepository.inventario(texto == null ? "" : texto.trim(),
                (filtro == null ? FiltroStock.TODOS : filtro).name(),
                PageRequest.of(pagina, tamanio, Sort.by("codigoProducto")));
    }

    public List<Producto> listarTodo() {
        return productoRepository.listarConCategoria();
    }

    public ResumenInventario resumen() {
        List<Producto> productos = productoRepository.findAll();
        BigDecimal unidades = BigDecimal.ZERO;
        long bajo = 0;
        long sin = 0;
        BigDecimal costo = BigDecimal.ZERO;
        BigDecimal venta = BigDecimal.ZERO;
        for (Producto p : productos) {
            BigDecimal stock = p.getStock().max(BigDecimal.ZERO);
            unidades = unidades.add(stock);
            if (p.isSinStock()) {
                sin++;
            } else if (p.isStockBajo()) {
                bajo++;
            }
            costo = costo.add(valorizado(p));
            venta = venta.add(p.getPrecio().multiply(stock));
        }
        return new ResumenInventario(productos.size(), unidades,
                costo.setScale(2, RoundingMode.HALF_UP), venta.setScale(2, RoundingMode.HALF_UP), bajo, sin);
    }

    public List<MovimientoInventario> kardex(Long productoId) {
        return movimientoRepository.kardex(productoId);
    }

    /** Stock x costo promedio (cero si el producto nunca se compro). */
    public static BigDecimal valorizado(Producto p) {
        if (p.getCostoPromedio() == null || p.getStock().signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return p.getCostoPromedio().multiply(p.getStock()).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------- comandos

    /**
     * Verifica que haya stock para todas las cantidades pedidas (agrupadas por producto)
     * y, si falta en alguno, informa todos los faltantes juntos.
     */
    public void validarDisponible(Map<Producto, BigDecimal> pedido) {
        String faltantes = pedido.entrySet().stream()
                .filter(e -> e.getValue().compareTo(e.getKey().getStock()) > 0)
                .map(e -> e.getKey().getNombre() + " (disponible " + texto(e.getKey().getStock().max(BigDecimal.ZERO))
                        + ", solicitado " + texto(e.getValue()) + ")")
                .collect(Collectors.joining("; "));
        if (!faltantes.isEmpty()) {
            throw new NegocioException("Stock insuficiente: " + faltantes + ".");
        }
    }

    @Transactional
    public MovimientoInventario entrada(Producto producto, int cantidad, BigDecimal costoUnitario,
                                        TipoMovimiento tipo, String documento, LocalDateTime fecha,
                                        Long ventaId, Long compraId) {
        return entrada(producto, BigDecimal.valueOf(cantidad), costoUnitario, tipo, documento, fecha, ventaId, compraId);
    }

    @Transactional
    public MovimientoInventario entrada(Producto producto, BigDecimal cantidad, BigDecimal costoUnitario,
                                        TipoMovimiento tipo, String documento, LocalDateTime fecha,
                                        Long ventaId, Long compraId) {
        if (!tipo.isEntrada()) {
            throw new IllegalArgumentException(tipo + " no es un movimiento de entrada");
        }
        BigDecimal anterior = producto.getStock().max(BigDecimal.ZERO);
        if (tipo == TipoMovimiento.COMPRA && costoUnitario != null) {
            producto.setCostoPromedio(costoPromedio(producto.getCostoPromedio(), anterior, costoUnitario, cantidad));
        }
        producto.setStock(producto.getStock().add(cantidad));
        return registrar(producto, tipo, cantidad, costoUnitario, documento, fecha, ventaId, compraId);
    }

    @Transactional
    public MovimientoInventario salida(Producto producto, int cantidad, TipoMovimiento tipo, String documento,
                                       LocalDateTime fecha, Long ventaId, Long compraId) {
        return salida(producto, BigDecimal.valueOf(cantidad), tipo, documento, fecha, ventaId, compraId);
    }

    @Transactional
    public MovimientoInventario salida(Producto producto, BigDecimal cantidad, TipoMovimiento tipo, String documento,
                                       LocalDateTime fecha, Long ventaId, Long compraId) {
        if (tipo.isEntrada()) {
            throw new IllegalArgumentException(tipo + " no es un movimiento de salida");
        }
        if (cantidad.compareTo(producto.getStock()) > 0) {
            throw new NegocioException("Stock insuficiente de " + producto.getNombre() + ": disponible "
                    + texto(producto.getStock().max(BigDecimal.ZERO)) + ", se requieren " + texto(cantidad) + ".");
        }
        producto.setStock(producto.getStock().subtract(cantidad));
        return registrar(producto, tipo, cantidad, null, documento, fecha, ventaId, compraId);
    }

    /** Cantidad sin ceros sobrantes: 2, 0.5, 1.25. */
    public static String texto(BigDecimal cantidad) {
        return cantidad == null ? "0" : cantidad.stripTrailingZeros().toPlainString();
    }

    private MovimientoInventario registrar(Producto producto, TipoMovimiento tipo, BigDecimal cantidad, BigDecimal costo,
                                           String documento, LocalDateTime fecha, Long ventaId, Long compraId) {
        return movimientoRepository.save(MovimientoInventario.builder()
                .producto(producto)
                .tipo(tipo)
                .cantidad(cantidad)
                .stockResultante(producto.getStock())
                .costoUnitario(costo)
                .documento(documento)
                .fecha(fecha)
                .ventaId(ventaId)
                .compraId(compraId)
                .build());
    }

    /** Promedio ponderado: (stock x costo actual + cantidad x costo nuevo) / (stock + cantidad). */
    static BigDecimal costoPromedio(BigDecimal actual, BigDecimal stock, BigDecimal nuevo, BigDecimal cantidad) {
        if (actual == null || stock.signum() <= 0) {
            return nuevo.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal total = actual.multiply(stock).add(nuevo.multiply(cantidad));
        return total.divide(stock.add(cantidad), 4, RoundingMode.HALF_UP);
    }
}
