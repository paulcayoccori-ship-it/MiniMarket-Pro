package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.MetodoPago;
import com.ejemplo.crudpersona.model.Persona;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.TipoComprobante;
import com.ejemplo.crudpersona.model.Venta;
import com.ejemplo.crudpersona.repository.VentaRepository;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ejecuta los seeders sobre una base SQLite temporal y genera PDF y Excel reales. */
@SpringBootTest
class VentasIntegracionTest {

    @DynamicPropertySource
    static void baseTemporal(DynamicPropertyRegistry registro) throws Exception {
        File db = Files.createTempFile("ventas-test", ".db").toFile();
        db.deleteOnExit();
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + db.getAbsolutePath());
        registro.add("app.seeder.enabled", () -> "true");
        registro.add("spring.jpa.show-sql", () -> "false");
        registro.add("logging.level.org.hibernate.SQL", () -> "WARN");
    }

    @Autowired VentaService ventaService;
    @Autowired VentaRepository ventaRepository;
    @Autowired PersonaService personaService;
    @Autowired ProductoService productoService;
    @Autowired UnidadMedidaService unidadService;
    @Autowired ReporteVentasService reporteService;
    @Autowired ComprobantePdfService pdfService;
    @Autowired ReporteExcelService excelService;

    @Test
    void seederGeneraVentasConsistentes() {
        List<Venta> ventas = ventaRepository.findAll();
        assertThat(ventas).hasSizeGreaterThanOrEqualTo(250);

        Set<String> comprobantes = new HashSet<>();
        for (Venta v : ventas) {
            assertThat(comprobantes.add(v.getComprobante())).as("comprobante unico").isTrue();
            assertThat(v.getBaseImponible().add(v.getIgv())).isEqualByComparingTo(v.getTotal());
            if (!v.isAplicaIgv()) {
                assertThat(v.getIgv()).isEqualByComparingTo("0");
            }
        }
        assertThat(ventas).anyMatch(Venta::isAnulada);
    }

    @Test
    void autocompletadoDePersonasYProductos() {
        Persona alguien = personaService.listar("", 0, 1).getContent().get(0);

        assertThat(personaService.autocompletar(alguien.getDni().substring(0, 4)))
                .allMatch(p -> p.getDni().startsWith(alguien.getDni().substring(0, 4)));
        // Palabras en cualquier orden: "apellido nombre".
        String inverso = alguien.getApellido().split(" ")[0] + " " + alguien.getNombre();
        assertThat(personaService.autocompletar(inverso)).contains(alguien);
        assertThat(personaService.buscarPorDni(alguien.getDni())).contains(alguien);

        assertThat(productoService.autocompletar("arroz")).isNotEmpty()
                .allMatch(p -> p.getNombre().toLowerCase().contains("arroz"));
        assertThat(productoService.autocompletar("PRD-001")).extracting(Producto::getCodigoProducto)
                .contains("PRD-001");
    }

    @Test
    void registraVentaConCorrelativoEIgv() {
        Persona cliente = personaService.listar("", 0, 1).getContent().get(0);
        Producto producto = productoService.autocompletar("PRD-001").get(0);
        String esperado = ventaService.siguienteComprobante(TipoComprobante.BOLETA);

        Venta venta = ventaService.registrar(new NuevaVenta(TipoComprobante.BOLETA, cliente.getId(),
                true, false, "Prueba", List.of(new LineaCarrito(producto.getId(), 2, new BigDecimal("50.00")))));

        assertThat(venta.getComprobante()).isEqualTo(esperado);
        assertThat(venta.getBaseImponible()).isEqualByComparingTo("100.00");
        assertThat(venta.getIgv()).isEqualByComparingTo("18.00");
        assertThat(venta.getTotal()).isEqualByComparingTo("118.00");

        assertThatThrownBy(() -> personaService.eliminar(cliente.getId()))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> productoService.eliminar(producto.getId()))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void productoAGranelConStockInicialYVentaEnEfectivoConVuelto() {
        String codigo = productoService.siguienteCodigoGranel();
        assertThat(codigo).startsWith("GR-");
        var categoria = productoService.crearCategoria("Granos a granel prueba");
        assertThatThrownBy(() -> productoService.crearCategoria("granos A GRANEL prueba"))
                .isInstanceOf(NegocioException.class);

        Producto arroz = productoService.guardar(Producto.builder()
                .nombre("Arroz a granel prueba").codigoProducto(codigo).granel(true)
                .precio(new BigDecimal("4.50")).precioCompra(new BigDecimal("3.20"))
                .unidad(unidadService.kilo())
                .categoria(categoria).stockMinimo(5).build(), new BigDecimal("20"));
        assertThat(arroz.getStock()).isEqualByComparingTo("20");
        assertThat(arroz.isVentaFraccionada()).isTrue();
        assertThat(arroz.isAGranel()).isTrue();
        assertThat(productoService.buscarPorCodigo(codigo)).isPresent();

        Persona generico = personaService.clienteGenerico();
        assertThat(personaService.clienteGenerico().getId()).isEqualTo(generico.getId());

        Venta venta = ventaService.registrar(new NuevaVenta(TipoComprobante.NOTA_VENTA, generico.getId(),
                true, true, null, List.of(new LineaCarrito(arroz.getId(), 2, new BigDecimal("4.50"))),
                MetodoPago.EFECTIVO, new BigDecimal("20")));
        assertThat(venta.getTotal()).isEqualByComparingTo("9.00");
        assertThat(venta.getVuelto()).isEqualByComparingTo("11.00");

        assertThatThrownBy(() -> ventaService.registrar(new NuevaVenta(TipoComprobante.NOTA_VENTA, generico.getId(),
                true, true, null, List.of(new LineaCarrito(arroz.getId(), 1, new BigDecimal("4.50"))),
                MetodoPago.EFECTIVO, new BigDecimal("2"))))
                .isInstanceOf(NegocioException.class);

        Venta yape = ventaService.registrar(new NuevaVenta(TipoComprobante.BOLETA, generico.getId(),
                true, true, null, List.of(new LineaCarrito(arroz.getId(), 1, new BigDecimal("4.50"))),
                MetodoPago.YAPE, null));
        assertThat(yape.getMontoRecibido()).isEqualByComparingTo("4.50");
        assertThat(yape.getVuelto()).isEqualByComparingTo("0");

        // Venta por kilo: 0.750 kg x 4.50 = 3.38 y el stock baja a 16.25 kg.
        Venta kilos = ventaService.registrar(new NuevaVenta(TipoComprobante.NOTA_VENTA, generico.getId(),
                true, true, null, List.of(new LineaCarrito(arroz.getId(), new BigDecimal("0.750"), new BigDecimal("4.50"))),
                MetodoPago.EFECTIVO, new BigDecimal("5")));
        assertThat(kilos.getTotal()).isEqualByComparingTo("3.38");
        assertThat(productoService.buscarPorCodigo(codigo).orElseThrow().getStock()).isEqualByComparingTo("16.25");

        // Un producto por unidad no acepta medias unidades.
        Producto porUnidad = productoService.autocompletar("PRD-002").get(0);
        assertThatThrownBy(() -> ventaService.registrar(new NuevaVenta(TipoComprobante.NOTA_VENTA, generico.getId(),
                true, true, null, List.of(new LineaCarrito(porUnidad.getId(), new BigDecimal("0.5"), porUnidad.getPrecio())),
                MetodoPago.YAPE, null)))
                .isInstanceOf(NegocioException.class);

        // Unidades nuevas creadas por el usuario.
        var sixPack = unidadService.crear("Six pack", "six", false);
        assertThat(unidadService.listar()).extracting(u -> u.getNombre()).contains("Unidad", "Kilo", "Six pack");
        assertThatThrownBy(() -> unidadService.crear("six PACK", "", false)).isInstanceOf(NegocioException.class);
        assertThat(sixPack.isDecimales()).isFalse();
    }

    @Test
    void generaPdfYExcel() throws Exception {
        Venta venta = ventaRepository.findAll().get(0);
        File pdf = Files.createTempFile("comprobante", ".pdf").toFile();
        pdfService.generar(venta.getId(), pdf);
        assertThat(Files.readString(pdf.toPath(), StandardCharsets.ISO_8859_1)).startsWith("%PDF");

        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusDays(119);
        File xlsx = Files.createTempFile("reporte", ".xlsx").toFile();
        excelService.generar(desde, hasta, xlsx);
        try (XSSFWorkbook libro = new XSSFWorkbook(new FileInputStream(xlsx))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(5);
            assertThat(libro.getSheet("Ventas").getLastRowNum()).isGreaterThan(200);
            assertThat(libro.getSheet("Detalle").getLastRowNum()).isGreaterThan(libro.getSheet("Ventas").getLastRowNum());
        }

        // Hasta 92 dias la serie es diaria; con mas dias se agrupa por mes.
        var diario = reporteService.resumen(hasta.minusDays(89), hasta);
        assertThat(diario.seriePorMes()).isFalse();
        assertThat(diario.serie()).hasSize(90);
        assertThat(diario.baseImponible().add(diario.igv())).isEqualByComparingTo(diario.total());

        var mensual = reporteService.resumen(desde, hasta);
        assertThat(mensual.seriePorMes()).isTrue();
        assertThat(mensual.comprobantes() + mensual.anuladas()).isGreaterThanOrEqualTo(250);
    }
}
