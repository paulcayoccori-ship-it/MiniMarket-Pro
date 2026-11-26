package com.ejemplo.crudpersona.seeder;

import com.ejemplo.crudpersona.model.Persona;
import com.ejemplo.crudpersona.repository.PersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registra 100 personas distintas (nombre completo, DNI y correo unicos).
 * Usa una semilla fija, por lo que siempre genera los mismos datos: al volver a
 * ejecutarse omite los DNI que ya existen en la base de datos.
 */
@Component
@RequiredArgsConstructor
public class PersonaSeeder {

    private static final int CANTIDAD = 100;
    private static final long SEMILLA = 2026L;

    private static final List<String> NOMBRES = List.of(
            "Maria", "Jose", "Lucia", "Carlos", "Ana", "Luis", "Rosa", "Pedro", "Elena", "Jorge",
            "Carmen", "Miguel", "Sofia", "Diego", "Valeria", "Juan", "Camila", "Andres", "Gabriela", "Ricardo",
            "Daniela", "Fernando", "Patricia", "Alejandro", "Milagros", "Renzo", "Claudia", "Hugo", "Fiorella", "Sebastian");

    private static final List<String> APELLIDOS = List.of(
            "Quispe", "Flores", "Garcia", "Rojas", "Mamani", "Torres", "Huaman", "Diaz", "Vargas", "Castillo",
            "Ramirez", "Chavez", "Mendoza", "Gutierrez", "Salazar", "Ramos", "Espinoza", "Ccori", "Paredes", "Silva",
            "Cruz", "Sanchez", "Condori", "Herrera", "Medina", "Aguilar", "Rivera", "Navarro", "Cardenas", "Leon");

    private static final List<String> DOMINIOS = List.of(
            "gmail.com", "hotmail.com", "outlook.com", "yahoo.com", "correo.pe");

    private final PersonaRepository repository;

    @Transactional
    public int sembrar() {
        Set<String> dnisExistentes = repository.findAll().stream()
                .map(Persona::getDni)
                .collect(Collectors.toSet());

        Random azar = new Random(SEMILLA);
        Set<String> nombresUsados = new HashSet<>();
        Set<String> dnisUsados = new HashSet<>();
        Set<String> correosUsados = new HashSet<>();
        List<Persona> nuevas = new ArrayList<>();

        int generadas = 0;
        while (generadas < CANTIDAD) {
            String nombre = elegir(azar, NOMBRES);
            String apellido1 = elegir(azar, APELLIDOS);
            String apellido2 = elegir(azar, APELLIDOS);
            if (apellido1.equals(apellido2) || !nombresUsados.add(nombre + apellido1 + apellido2)) {
                continue;
            }

            String dni;
            do {
                dni = String.valueOf(10_000_000 + azar.nextInt(80_000_000));
            } while (!dnisUsados.add(dni));

            String correo;
            do {
                correo = (nombre + "." + apellido1 + azar.nextInt(100)).toLowerCase()
                        + "@" + elegir(azar, DOMINIOS);
            } while (!correosUsados.add(correo));

            // Datos de contacto incompletos a proposito para probar vistas y graficos.
            boolean conCorreo = azar.nextDouble() < 0.85;
            boolean conTelefono = azar.nextDouble() < 0.70;
            String telefono = "9" + String.format("%08d", azar.nextInt(100_000_000));

            generadas++;
            if (dnisExistentes.contains(dni)) {
                continue;
            }
            nuevas.add(Persona.builder()
                    .nombre(nombre)
                    .apellido(apellido1 + " " + apellido2)
                    .dni(dni)
                    .email(conCorreo ? correo : null)
                    .telefono(conTelefono ? telefono : null)
                    .build());
        }
        repository.saveAll(nuevas);
        return nuevas.size();
    }

    private static String elegir(Random azar, List<String> opciones) {
        return opciones.get(azar.nextInt(opciones.size()));
    }
}
