package com.ejemplo.crudpersona;

import javafx.application.Application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Launcher {
    public static void main(String[] args) {
        configurarCarpetaDeDatos();
        Application.launch(JavaFxApplication.class, args);
    }

    /**
     * Cuando el sistema se ejecuta instalado (con el instalador de Windows), la carpeta del programa
     * (Archivos de programa) es de solo lectura. Por eso la base de datos y las fotos se guardan en
     * %LOCALAPPDATA%\MiniMarket Pro. Al ejecutarlo desde el codigo fuente (mvnw javafx:run) se sigue
     * usando la carpeta del proyecto, como antes.
     */
    private static void configurarCarpetaDeDatos() {
        boolean instalado = System.getProperty("jpackage.app-path") != null;
        String carpetaElegida = System.getProperty("app.datos.dir");
        if (!instalado && carpetaElegida == null) {
            return;
        }
        Path carpeta;
        if (carpetaElegida != null) {
            carpeta = Paths.get(carpetaElegida);
        } else {
            String base = System.getenv("LOCALAPPDATA");
            if (base == null || base.isBlank()) {
                base = System.getProperty("user.home");
            }
            carpeta = Paths.get(base, "MiniMarket Pro");
        }
        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            System.err.println("No se pudo crear la carpeta de datos " + carpeta + ": " + e.getMessage());
            return;
        }
        Path db = carpeta.resolve("minimarket.db").toAbsolutePath();
        System.setProperty("spring.datasource.url", "jdbc:sqlite:" + db.toString().replace('\\', '/'));
        System.setProperty("app.imagenes.dir", carpeta.resolve("imagenes-productos").toAbsolutePath().toString());
    }
}
