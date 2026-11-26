package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.EstadoVenta;
import com.ejemplo.crudpersona.model.MetodoPago;
import com.ejemplo.crudpersona.model.Persona;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.TipoComprobante;
import com.ejemplo.crudpersona.model.TipoMovimiento;
import com.ejemplo.crudpersona.model.Venta;
import com.ejemplo.crudpersona.model.VentaDetalle;
import com.ejemplo.crudpersona.repository.PersonaRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.repository.VentaRepository;
import com.ejemplo.crudpersona.service.CalculadoraIgv.Totales;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class VentaService {

    private static final LocalDate FECHA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final LocalDate FECHA_MAXIMA = LocalDate.of(2100, 12, 31);
    private static final LocalTime FIN_DEL_DIA = LocalTime.of(23, 59, 59, 999_000_000);

    private final VentaRepository repository;
    private final PersonaRepository personaRepository;
    private final ProductoRepository productoRepository;
    private final InventarioService inventarioService;

    /** Linea del carrito tal como la arma la pantalla de venta. */
    public record LineaCarrito(Long productoId, BigDecimal cantidad, BigDecimal precioUnitario) {
        public LineaCarrito(Long productoId, int cantidad, BigDecimal precioUnitario) {
            this(productoId, BigDecimal.valueOf(cantidad), precioUnitario);
        }
    }

    public record NuevaVenta(TipoComprobante tipo,
                             Long clienteId,
                             boolean aplicaIgv,
                             boolean preciosIncluyenIgv,
                             String observacion,
                             List<LineaCarrito> lineas,
                             MetodoPago metodoPago,
                             BigDecimal montoRecibido) {

        /** Venta sin datos de cobro (historico, seeder y pruebas). */
        public NuevaVenta(TipoComprobante tipo, Long clienteId, boolean aplicaIgv, boolean preciosIncluyenIgv,
                          String observacion, List<LineaCarrito> lineas) {
            this(tipo, clienteId, aplicaIgv, preciosIncluyenIgv, observacion, lineas, null, null);
        }
    }

    public record Totalizado(long comprobantes, BigDecimal baseImponible, BigDecimal igv, BigDecimal total) {}

    // ------------------------------------------------------------ consultas

    public Page<Venta> listar(String texto, LocalDate desde, LocalDate hasta, int pagina, int tamanio) {
        return repository.buscar(
                texto == null ? "" : texto.trim(),
                inicio(desde), fin(hasta),
                PageRequest.of(pagina, tamanio, Sort.by("fecha").descending().and(Sort.by("id").descending())));
    }

    /** Totales de las ventas emitidas (sin anuladas) que cumplen el mismo filtro que {@link #listar}. */
    public Totalizado totalizar(String texto, LocalDate desde, LocalDate hasta) {
        Object[] fila = repository.totalizar(texto == null ? "" : texto.trim(), EstadoVenta.EMITIDA,
                inicio(desde), fin(hasta)).get(0);
        return new Totalizado(((Number) fila[0]).longValue(), decimal(fila[1]), decimal(fila[2]), decimal(fila[3]));
    }

    /** Venta con cliente y detalle cargados (para el PDF y la vista de detalle). */
    public Venta obtener(Long id) {
        return repository.buscarConDetalle(id)
                .orElseThrow(() -> new NegocioException("La venta ya no existe en la base de datos."));
    }

    /** Fecha de la venta mas antigua (hoy si aun no hay ventas). */
    public LocalDate primeraFecha() {
        LocalDateTime primera = repository.primeraFecha();
        return primera == null ? LocalDate.now() : primera.toLocalDate();
    }

    public String siguienteComprobante(TipoComprobante tipo) {
        return Venta.formatearComprobante(tipo.getSerie(), siguienteNumero(tipo.getSerie()));
    }

    // ------------------------------------------------------------- comandos

    @Transactional
    public Venta registrar(NuevaVenta solicitud) {
        return registrar(solicitud, LocalDateTime.now());
    }

    /** Variante con fecha explicita, usada por el seeder para generar historico. */
    @Transactional
    public Venta registrar(NuevaVenta solicitud, LocalDateTime fecha) {
        validar(solicitud);

        Persona cliente = personaRepository.findById(solicitud.clienteId())
                .orElseThrow(() -> new NegocioException("El cliente seleccionado ya no existe."));
        Map<Long, Producto> productos = productoRepository.findAllById(
                        solicitud.lineas().stream().map(LineaCarrito::productoId).toList()).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        TipoComprobante tipo = solicitud.tipo();
        int numero = siguienteNumero(tipo.getSerie());
        Venta venta = Venta.builder()
                .tipoComprobante(tipo)
                .serie(tipo.getSerie())
                .numero(numero)
                .comprobante(Venta.formatearComprobante(tipo.getSerie(), numero))
                .fecha(fecha)
                .cliente(cliente)
                .aplicaIgv(solicitud.aplicaIgv())
                .preciosIncluyenIgv(solicitud.preciosIncluyenIgv())
                .estado(EstadoVenta.EMITIDA)
                .observacion(textoONull(solicitud.observacion()))
                .build();

        if (solicitud.lineas().stream().anyMatch(l -> !productos.containsKey(l.productoId()))) {
            throw new NegocioException("Un producto del carrito ya no existe en el catalogo.");
        }
        // Se valida el stock de todo el carrito antes de tocar nada.
        inventarioService.validarDisponible(solicitud.lineas().stream()
                .collect(Collectors.groupingBy(l -> productos.get(l.productoId()),
                        Collectors.reducing(BigDecimal.ZERO, LineaCarrito::cantidad, BigDecimal::add))));

        for (LineaCarrito linea : solicitud.lineas()) {
            Producto producto = productos.get(linea.productoId());
            if (!producto.isVentaFraccionada() && linea.cantidad().stripTrailingZeros().scale() > 0) {
                throw new NegocioException(producto.getNombre() + " se vende por " + producto.getAbreviaturaUnidad()
                        + ": la cantidad debe ser un numero entero.");
            }
            venta.agregarDetalle(VentaDetalle.builder()
                    .producto(producto)
                    .codigoProducto(producto.getCodigoProducto())
                    .nombreProducto(producto.getNombre())
                    .cantidad(linea.cantidad())
                    .precioUnitario(linea.precioUnitario())
                    .importe(CalculadoraIgv.importe(linea.cantidad(), linea.precioUnitario()))
                    .build());
        }

        Totales totales = CalculadoraIgv.calcular(
                venta.getDetalles().stream().map(VentaDetalle::getImporte).toList(),
                venta.isAplicaIgv(), venta.isPreciosIncluyenIgv());
        venta.setBaseImponible(totales.baseImponible());
        venta.setIgv(totales.igv());
        venta.setTotal(totales.total());
        venta.setTasaIgv(totales.tasa());

        if (solicitud.metodoPago() != null) {
            BigDecimal recibido = solicitud.montoRecibido() == null || solicitud.metodoPago() != MetodoPago.EFECTIVO
                    ? totales.total()
                    : solicitud.montoRecibido().setScale(2, RoundingMode.HALF_UP);
            if (recibido.compareTo(totales.total()) < 0) {
                throw new NegocioException("El importe recibido (S/ " + recibido
                        + ") es menor que el total a pagar (S/ " + totales.total() + ").");
            }
            venta.setMetodoPago(solicitud.metodoPago());
            venta.setMontoRecibido(recibido);
            venta.setVuelto(recibido.subtract(totales.total()));
        }

        Venta guardada = repository.save(venta);
        for (VentaDetalle d : guardada.getDetalles()) {
            inventarioService.salida(d.getProducto(), d.getCantidad(), TipoMovimiento.VENTA,
                    guardada.getComprobante(), fecha, guardada.getId(), null);
        }
        log.info("Venta registrada: {} total={}", guardada.getComprobante(), guardada.getTotal());
        return guardada;
    }

    /** Anula el comprobante y devuelve al stock las unidades vendidas. */
    @Transactional
    public void anular(Long id) {
        anular(id, LocalDateTime.now());
    }

    /** Variante con fecha explicita para el seeder (la devolucion queda en su lugar del kardex). */
    @Transactional
    public void anular(Long id, LocalDateTime ahora) {
        Venta venta = obtener(id);
        if (venta.isAnulada()) {
            throw new NegocioException("El comprobante " + venta.getComprobante() + " ya estaba anulado.");
        }
        for (VentaDetalle d : venta.getDetalles()) {
            inventarioService.entrada(d.getProducto(), d.getCantidad(), null, TipoMovimiento.ANULACION_VENTA,
                    venta.getComprobante() + " (anulada)", ahora, venta.getId(), null);
        }
        venta.setEstado(EstadoVenta.ANULADA);
        log.info("Venta anulada: {}", venta.getComprobante());
    }

    // ---------------------------------------------------------------- apoyo

    private void validar(NuevaVenta s) {
        if (s.tipo() == null) {
            throw new NegocioException("Selecciona el tipo de comprobante.");
        }
        if (s.clienteId() == null) {
            throw new NegocioException("Selecciona un cliente para la venta.");
        }
        if (s.lineas() == null || s.lineas().isEmpty()) {
            throw new NegocioException("Agrega al menos un producto al carrito.");
        }
        for (LineaCarrito l : s.lineas()) {
            if (l.cantidad() == null || l.cantidad().signum() <= 0) {
                throw new NegocioException("Todas las cantidades deben ser mayores a cero.");
            }
            if (l.precioUnitario() == null || l.precioUnitario().signum() <= 0) {
                throw new NegocioException("Todos los precios deben ser mayores a cero.");
            }
        }
    }

    private int siguienteNumero(String serie) {
        Integer ultimo = repository.ultimoNumero(serie);
        return ultimo == null ? 1 : ultimo + 1;
    }

    static LocalDateTime inicio(LocalDate fecha) {
        return (fecha == null ? FECHA_MINIMA : fecha).atStartOfDay();
    }

    static LocalDateTime fin(LocalDate fecha) {
        return (fecha == null ? FECHA_MAXIMA : fecha).atTime(FIN_DEL_DIA);
    }

    /** SQLite puede devolver las sumas como Double o Long: se normalizan a 2 decimales. */
    static BigDecimal decimal(Object valor) {
        if (valor instanceof BigDecimal b) {
            return b.setScale(2, RoundingMode.HALF_UP);
        }
        return valor == null
                ? BigDecimal.ZERO.setScale(2)
                : new BigDecimal(valor.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private static String textoONull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
