package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.Compra;
import com.ejemplo.crudpersona.model.MovimientoInventario;
import com.ejemplo.crudpersona.model.Persona;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.TipoComprobante;
import com.ejemplo.crudpersona.model.TipoDocumentoCompra;
import com.ejemplo.crudpersona.model.Venta;
import com.ejemplo.crudpersona.repository.CompraRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.service.CompraService.LineaCompra;
import com.ejemplo.crudpersona.service.CompraService.NuevaCompra;
import com.ejemplo.crudpersona.service.VentaService.LineaCarrito;
import com.ejemplo.crudpersona.service.VentaService.NuevaVenta;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stock y kardex sobre una base SQLite temporal con todos los seeders. */
@SpringBootTest
class InventarioIntegracionTest {

    @DynamicPropertySource
    static void baseTemporal(DynamicPropertyRegistry registro) throws Exception {
        File db = Files.createTempFile("inventario-test", ".db").toFile();
        db.deleteOnExit();
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.getAbsolutePath());
        registro.add("app.seeder.enabled", () -> "true");
        registro.add("spring.jpa.show-sql", () -> "false");
        registro.add("logging.level.org.hibernate.SQL", () -> "WARN");
    }

    @Autowired InventarioService inventarioService;
    @Autowired CompraService compraService;
    @Autowired VentaService ventaService;
    @Autowired ProductoService productoService;
    @Autowired PersonaService personaService;
    @Autowired ProductoRepository productoRepository;
    @Autowired CompraRepository compraRepository;
    @Autowired ReporteInventarioExcelService excelService;

    @Test
    void exportaInventarioYComprasAExcel() throws Exception {
        File inventario = Files.createTempFile("inventario", ".xlsx").toFile();
        excelService.generarInventario(inventario);
        try (XSSFWorkbook libro = new XSSFWorkbook(new FileInputStream(inventario))) {
            assertThat(libro.getSheet("Inventario").getLastRowNum()).isGreaterThanOrEqualTo(100);
            assertThat(libro.getSheet("Kardex").getLastRowNum()).isGreaterThan(100);
        }
        File compras = Files.createTempFile("compras", ".xlsx").toFile();
        excelService.generarCompras(LocalDate.now().minusYears(1), LocalDate.now(), compras);
        try (XSSFWorkbook libro = new XSSFWorkbook(new FileInputStream(compras))) {
            assertThat(libro.getSheet("Compras").getLastRowNum()).isGreaterThan(5);
            assertThat(libro.getSheet("Detalle").getLastRowNum()).isGreaterThan(100);
        }
    }

    @Test
    void elKardexCuadraConElStockDeCadaProducto() {
        assertThat(compraRepository.count()).isGreaterThan(5);
        for (Producto p : productoRepository.findAll()) {
            List<MovimientoInventario> kardex = inventarioService.kardex(p.getId());
            BigDecimal saldo = BigDecimal.ZERO;
            for (MovimientoInventario m : kardex) {
                saldo = saldo.add(m.getEntrada()).subtract(m.getSalida());
                assertThat(saldo.signum()).as("saldo nunca negativo en %s", p.getCodigoProducto()).isGreaterThanOrEqualTo(0);
                assertThat(m.getStockResultante()).as("saldo cronologico de %s", p.getCodigoProducto()).isEqualByComparingTo(saldo);
            }
            assertThat(p.getStock()).as("stock de %s", p.getCodigoProducto()).isEqualByComparingTo(saldo);
            if (!kardex.isEmpty()) {
                assertThat(p.getCostoPromedio()).isPositive();
            }
        }
    }

    @Test
    void compraSumaVentaRestaYAnulacionesRevierten() {
        Producto p = productoService.autocompletar("PRD-010").get(0);
        Persona cliente = personaService.listar("", 0, 1).getContent().get(0);
        int inicial = stock(p);

        Compra compra = compraService.registrar(new NuevaCompra("20100055237", "ALICORP S.A.A.",
                TipoDocumentoCompra.FACTURA, "F001-999001", LocalDate.now(), true, true, null,
                List.of(new LineaCompra(p.getId(), 10, new BigDecimal("11.80")))));
        assertThat(stock(p)).isEqualTo(inicial + 10);
        assertThat(compra.getBaseImponible()).isEqualByComparingTo("100.00");
        assertThat(compra.getIgv()).isEqualByComparingTo("18.00");

        Venta venta = ventaService.registrar(new NuevaVenta(TipoComprobante.BOLETA, cliente.getId(), true, true,
                null, List.of(new LineaCarrito(p.getId(), 4, p.getPrecio()))));
        assertThat(stock(p)).isEqualTo(inicial + 6);

        assertThatThrownBy(() -> ventaService.registrar(new NuevaVenta(TipoComprobante.BOLETA, cliente.getId(),
                true, true, null, List.of(new LineaCarrito(p.getId(), inicial + 1000, p.getPrecio())))))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Stock insuficiente");
        assertThat(stock(p)).as("una venta rechazada no toca el stock").isEqualTo(inicial + 6);

        ventaService.anular(venta.getId());
        assertThat(stock(p)).isEqualTo(inicial + 10);

        compraService.anular(compra.getId());
        assertThat(stock(p)).isEqualTo(inicial);

        assertThatThrownBy(() -> compraService.registrar(new NuevaCompra("20100055237", "ALICORP S.A.A.",
                TipoDocumentoCompra.FACTURA, "F001-999001", LocalDate.now().plusDays(3), true, true, null,
                List.of(new LineaCompra(p.getId(), 1, BigDecimal.ONE)))))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void noSePuedeAnularUnaCompraYaVendida() {
        Producto p = productoService.autocompletar("PRD-020").get(0);
        Persona cliente = personaService.listar("", 0, 1).getContent().get(0);
        int inicial = stock(p);
        // Se vende todo el stock previo y parte de lo comprado.
        Compra compra = compraService.registrar(new NuevaCompra("20611122233", "PET FOOD DISTRIBUCIONES S.A.C.",
                TipoDocumentoCompra.FACTURA, "F002-000777", LocalDate.now(), true, false, null,
                List.of(new LineaCompra(p.getId(), 5, new BigDecimal("2.00")))));
        ventaService.registrar(new NuevaVenta(TipoComprobante.BOLETA, cliente.getId(), true, true, null,
                List.of(new LineaCarrito(p.getId(), inicial + 2, p.getPrecio()))));

        assertThatThrownBy(() -> compraService.anular(compra.getId()))
                .isInstanceOf(NegocioException.class).hasMessageContaining("ya se vendio");
        assertThat(stock(p)).isEqualTo(3);
    }

    @Test
    void editarProductoConservaElStockYProveedorNuevoSeRegistra() {
        Producto p = productoService.autocompletar("PRD-030").get(0);
        int stock = stock(p);
        p.setNombre(p.getNombre() + " (editado)");
        p.setStock(BigDecimal.ZERO);
        productoService.guardar(p);
        assertThat(stock(p)).isEqualTo(stock);

        compraService.registrar(new NuevaCompra("20999888771", "Proveedor Nuevo S.A.C.",
                TipoDocumentoCompra.BOLETA, "B001-12", LocalDate.now(), false, true, null,
                List.of(new LineaCompra(p.getId(), 2, new BigDecimal("5.00")))));
        assertThat(stock(p)).isEqualTo(stock + 2);
    }

    @Test
    void costoPromedioPonderado() {
        assertThat(InventarioService.costoPromedio(new BigDecimal("10"), BigDecimal.TEN, new BigDecimal("16"), BigDecimal.valueOf(5)))
                .isEqualByComparingTo("12.0000");
        assertThat(InventarioService.costoPromedio(null, BigDecimal.ZERO, new BigDecimal("7.5"), BigDecimal.valueOf(3)))
                .isEqualByComparingTo("7.5");
    }

    private int stock(Producto p) {
        return productoRepository.findById(p.getId()).orElseThrow().getStock().intValueExact();
    }
}
