package com.ejemplo.crudpersona.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Carga datos de prueba al iniciar la aplicacion: 100 registros por entidad, 10 proveedores,
 * inventario inicial (compras de apertura) y 250 ventas con sus reposiciones.
 * Se activa con {@code app.seeder.enabled=true} en application.properties.
 * Cada seeder es idempotente, asi que reiniciar la aplicacion no duplica datos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class DatabaseSeeder implements ApplicationRunner {

    private final CategoriaSeeder categoriaSeeder;
    private final ProductoSeeder productoSeeder;
    private final PersonaSeeder personaSeeder;
    private final ProveedorSeeder proveedorSeeder;
    private final InventarioSeeder inventarioSeeder;
    private final VentaSeeder ventaSeeder;

    @Override
    public void run(ApplicationArguments args) {
        // Orden importante: los productos se asocian a categorias ya registradas; el
        // inventario inicial necesita proveedores y productos; las ventas necesitan
        // clientes (personas) y stock.
        int categorias = categoriaSeeder.sembrar();
        int productos = productoSeeder.sembrar();
        int personas = personaSeeder.sembrar();
        int proveedores = proveedorSeeder.sembrar();
        int apertura = inventarioSeeder.sembrar();
        int ventas = ventaSeeder.sembrar();
        log.info("Seeder: {} categorias, {} productos, {} personas, {} proveedores, {} compras de apertura "
                + "y {} ventas nuevas registradas.", categorias, productos, personas, proveedores, apertura, ventas);
    }
}
