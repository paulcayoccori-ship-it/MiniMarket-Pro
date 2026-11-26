package com.ejemplo.crudpersona.seeder;

import com.ejemplo.crudpersona.model.Categoria;
import com.ejemplo.crudpersona.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registra 100 categorias distintas (codigos CAT-001 ... CAT-100).
 * Es idempotente: omite los codigos que ya existen en la base de datos.
 */
@Component
@RequiredArgsConstructor
public class CategoriaSeeder {

    static final List<String> NOMBRES = List.of(
            "Abarrotes", "Aceites y vinagres", "Aguas", "Arroces", "Azucar y endulzantes",
            "Bebidas gaseosas", "Bebidas energeticas", "Jugos y nectares", "Cervezas", "Vinos",
            "Licores", "Cafe", "Te e infusiones", "Chocolates", "Galletas",
            "Snacks salados", "Golosinas", "Cereales", "Menestras", "Pastas",
            "Harinas", "Conservas de pescado", "Conservas de frutas", "Salsas y aderezos", "Especias y condimentos",
            "Sopas instantaneas", "Leches", "Yogures", "Quesos", "Mantequillas y margarinas",
            "Huevos", "Embutidos", "Carnes de res", "Carnes de cerdo", "Aves",
            "Pescados y mariscos", "Frutas", "Verduras", "Tuberculos", "Congelados",
            "Helados", "Panaderia", "Pasteleria", "Comida preparada", "Productos organicos",
            "Productos sin gluten", "Alimentos para bebe", "Panales", "Cuidado del bebe", "Cuidado del cabello",
            "Cuidado de la piel", "Cuidado bucal", "Desodorantes", "Maquillaje", "Perfumes",
            "Afeitado", "Higiene femenina", "Papel higienico", "Detergentes", "Suavizantes",
            "Lavavajillas", "Desinfectantes", "Limpiadores multiuso", "Ambientadores", "Insecticidas",
            "Bolsas y descartables", "Utensilios de cocina", "Menaje", "Textil hogar", "Decoracion",
            "Iluminacion", "Ferreteria", "Herramientas", "Jardineria", "Pinturas",
            "Electricidad", "Televisores", "Audio", "Celulares", "Computadoras",
            "Tablets", "Accesorios tecnologicos", "Videojuegos", "Electrodomesticos grandes", "Electrodomesticos pequenos",
            "Climatizacion", "Utiles escolares", "Oficina", "Libros", "Juguetes",
            "Deportes", "Camping", "Bicicletas", "Ropa de hombre", "Ropa de mujer",
            "Ropa infantil", "Calzado", "Accesorios de moda", "Alimento para perros", "Alimento para gatos");

    private static final List<String> PLANTILLAS_DESCRIPCION = List.of(
            "Seccion de %s",
            "Variedad de %s para el dia a dia",
            "%s de marcas nacionales e importadas",
            "Todo en %s al mejor precio");

    private final CategoriaRepository repository;

    @Transactional
    public int sembrar() {
        Set<String> existentes = repository.findAll().stream()
                .map(Categoria::getCodigoCategoria)
                .collect(Collectors.toSet());

        List<Categoria> nuevas = new ArrayList<>();
        for (int i = 0; i < NOMBRES.size(); i++) {
            String codigo = String.format("CAT-%03d", i + 1);
            if (existentes.contains(codigo)) {
                continue;
            }
            String nombre = NOMBRES.get(i);
            // Una de cada 7 categorias queda sin descripcion (el campo es opcional).
            String plantilla = PLANTILLAS_DESCRIPCION.get(i % PLANTILLAS_DESCRIPCION.size());
            String descripcion = i % 7 == 6
                    ? null
                    : String.format(plantilla, plantilla.startsWith("%s") ? nombre : nombre.toLowerCase());
            nuevas.add(Categoria.builder()
                    .codigoCategoria(codigo)
                    .nombre(nombre)
                    .descripcion(descripcion)
                    .build());
        }
        repository.saveAll(nuevas);
        return nuevas.size();
    }
}
