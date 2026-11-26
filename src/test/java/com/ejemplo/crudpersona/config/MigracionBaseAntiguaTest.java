package com.ejemplo.crudpersona.config;

import com.ejemplo.crudpersona.model.MetodoPago;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.TipoComprobante;
import com.ejemplo.crudpersona.model.Venta;
import com.ejemplo.crudpersona.service.PersonaService;
import com.ejemplo.crudpersona.service.ProductoService;
import com.ejemplo.crudpersona.service.UnidadMedidaService;
import com.ejemplo.crudpersona.service.VentaService;
import com.ejemplo.crudpersona.service.VentaService.LineaCarrito;
import com.ejemplo.crudpersona.service.VentaService.NuevaVenta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abre una copia de la base de demostracion del repositorio (creada con la version anterior,
 * que tenia CHECK de cantidades enteras) y comprueba que tras la migracion se puede registrar
 * stock inicial y vender por kilo.
 */
@SpringBootTest
class MigracionBaseAntiguaTest {

    @DynamicPropertySource
    static void baseAntigua(DynamicPropertyRegistry registro) throws Exception {
        File copia = Files.createTempFile("base-antigua", ".db").toFile();
        copia.deleteOnExit();
        Path original = Path.of("src/test/resources/base-version-anterior.db");
        Files.copy(original, copia.toPath(), StandardCopyOption.REPLACE_EXISTING);
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + copia.getAbsolutePath());
        registro.add("app.seeder.enabled", () -> "false");
    }

    @Autowired ProductoService productoService;
    @Autowired UnidadMedidaService unidadService;
    @Autowired PersonaService personaService;
    @Autowired VentaService ventaService;

    @Test
    void permiteStockInicialYVentaPorKiloEnBaseAntigua() {
        Producto azucar = productoService.guardar(Producto.builder()
                .nombre("Azucar rubia a granel").codigoProducto(productoService.siguienteCodigoGranel()).granel(true)
                .precio(new BigDecimal("4.20")).precioCompra(new BigDecimal("3.50"))
                .unidad(unidadService.kilo()).stockMinimo(5).build(), new BigDecimal("10.5"));
        assertThat(azucar.getStock()).isEqualByComparingTo("10.5");

        Venta venta = ventaService.registrar(new NuevaVenta(TipoComprobante.NOTA_VENTA,
                personaService.clienteGenerico().getId(), true, true, null,
                List.of(new LineaCarrito(azucar.getId(), new BigDecimal("0.5"), new BigDecimal("4.20"))),
                MetodoPago.EFECTIVO, new BigDecimal("5")));
        assertThat(venta.getTotal()).isEqualByComparingTo("2.10");
        assertThat(productoService.buscarPorCodigo(azucar.getCodigoProducto()).orElseThrow().getStock())
                .isEqualByComparingTo("10");
    }
}
