package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.Categoria;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.TipoMovimiento;
import com.ejemplo.crudpersona.repository.CategoriaRepository;
import com.ejemplo.crudpersona.repository.CompraDetalleRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.repository.VentaDetalleRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductoService {

    private static final int TAMANIO_PAGINA = 15;
    private static final int LIMITE_SUGERENCIAS = 10;

    private final ProductoRepository repository;
    private final CategoriaRepository categoriaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final CompraDetalleRepository compraDetalleRepository;
    private final InventarioService inventarioService;

    public Page<Producto> listar(String filtro, int pagina) {
        return listar(filtro, pagina, TAMANIO_PAGINA);
    }

    public Page<Producto> listar(String filtro, int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio, Sort.by("id").ascending());
        if (filtro == null || filtro.isBlank()) {
            return repository.findAllConCategoria(pageable);
        }
        return repository.buscar(filtro.trim(), pageable);
    }

    /** Sugerencias para el carrito: cada palabra debe aparecer en el codigo o en el nombre. */
    public List<Producto> autocompletar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        Specification<Producto> spec = Specification.where(null);
        for (String palabra : texto.trim().toLowerCase().split("\\s+")) {
            String patron = "%" + palabra + "%";
            spec = spec.and((raiz, q, cb) -> cb.or(
                    cb.like(cb.lower(raiz.get("codigoProducto")), patron),
                    cb.like(cb.lower(raiz.get("nombre")), patron)));
        }
        return repository.findAll(spec,
                PageRequest.of(0, LIMITE_SUGERENCIAS, Sort.by("nombre"))).getContent();
    }

    public List<Categoria> listarCategorias() {
        return categoriaRepository.findAll(Sort.by("nombre").ascending());
    }

    /** Busqueda exacta por codigo de barras o codigo interno (lector de codigo de barras). */
    public java.util.Optional<Producto> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return java.util.Optional.empty();
        }
        return repository.findByCodigoProducto(codigo.trim());
    }

    /** Siguiente codigo interno libre para productos a granel o sin codigo: GR-0001, GR-0002... */
    public String siguienteCodigoGranel() {
        int n = (int) repository.count() + 1;
        String codigo;
        do {
            codigo = String.format("GR-%04d", n++);
        } while (repository.existsByCodigoProducto(codigo));
        return codigo;
    }

    /** Crea una categoria desde el formulario de producto, con codigo generado automaticamente. */
    @Transactional
    public Categoria crearCategoria(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            throw new NegocioException("Escribe el nombre de la nueva categoria.");
        }
        boolean repetida = categoriaRepository.findAll().stream()
                .anyMatch(c -> c.getNombre().equalsIgnoreCase(limpio));
        if (repetida) {
            throw new NegocioException("Ya existe la categoria \"" + limpio + "\".");
        }
        int n = (int) categoriaRepository.count() + 1;
        String codigo;
        do {
            codigo = String.format("CAT-%03d", n++);
        } while (categoriaRepository.findByCodigoCategoria(codigo).isPresent());
        Categoria nueva = categoriaRepository.save(Categoria.builder().nombre(limpio).codigoCategoria(codigo).build());
        log.info("Categoria creada desde producto: {} ({})", nueva.getNombre(), nueva.getCodigoCategoria());
        return nueva;
    }

    /**
     * Registra un producto nuevo y, si se indica, su stock inicial como movimiento de
     * inventario (queda en el kardex con el precio de compra como costo).
     */
    @Transactional
    public Producto guardar(@Valid Producto producto, BigDecimal stockInicial) {
        boolean esNuevo = producto.getId() == null;
        Producto guardado = guardar(producto);
        if (stockInicial != null && stockInicial.signum() > 0 && esNuevo) {
            if (guardado.getPrecioCompra() != null) {
                guardado.setCostoPromedio(guardado.getPrecioCompra().setScale(4, java.math.RoundingMode.HALF_UP));
            }
            inventarioService.entrada(guardado, stockInicial, guardado.getPrecioCompra(),
                    TipoMovimiento.INVENTARIO_INICIAL, "Stock inicial", java.time.LocalDateTime.now(), null, null);
        }
        return guardado;
    }

    @Transactional
    public Producto guardar(@Valid Producto producto) {
        repository.findByCodigoProducto(producto.getCodigoProducto()).ifPresent(existente -> {
            boolean esOtroProducto = producto.getId() == null
                    || !existente.getId().equals(producto.getId());
            if (esOtroProducto) {
                throw new NegocioException(
                        "Ya existe un producto registrado con el codigo " + producto.getCodigoProducto());
            }
        });

        // El stock y el costo solo cambian con compras y ventas: el formulario no los toca.
        if (producto.getId() != null) {
            repository.findById(producto.getId()).ifPresent(actual -> {
                producto.setStock(actual.getStock());
                producto.setCostoPromedio(actual.getCostoPromedio());
            });
        } else {
            producto.setStock(BigDecimal.ZERO);
            producto.setCostoPromedio(null);
        }
        if (producto.getStockMinimo() == null) {
            producto.setStockMinimo(5);
        }
        Producto guardado = repository.save(producto);
        log.info("Producto guardado: id={} codigo={}", guardado.getId(), guardado.getCodigoProducto());
        return guardado;
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new NegocioException("El producto ya no existe en la base de datos.");
        }
        if (ventaDetalleRepository.existsByProductoId(id)) {
            throw new NegocioException("No se puede eliminar: el producto figura en ventas registradas.");
        }
        if (compraDetalleRepository.existsByProductoId(id)) {
            throw new NegocioException("No se puede eliminar: el producto figura en compras registradas.");
        }
        repository.deleteById(id);
    }

    public List<Producto> catalogoVisual() { return repository.listarConCategoria(); }

    public long contar() {
        return repository.count();
    }
}

