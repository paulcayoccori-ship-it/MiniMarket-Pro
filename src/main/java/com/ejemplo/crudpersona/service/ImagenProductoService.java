package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.exception.NegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Fotos de productos subidas por el usuario. Se guardan reducidas (max. 600 px) en la
 * carpeta {@code imagenes-productos} junto a la base de datos, para que el catalogo cargue rapido.
 */
@Service
@Slf4j
public class ImagenProductoService {

    private static final int LADO_MAXIMO = 600;
    private final Path carpeta = Paths.get("imagenes-productos").toAbsolutePath();

    /** Copia la imagen elegida (PNG, JPG, GIF o BMP) y devuelve el nombre de archivo guardado. */
    public String guardar(File origen, String codigoProducto) {
        BufferedImage imagen;
        try {
            imagen = ImageIO.read(origen);
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer la imagen: " + e.getMessage());
        }
        if (imagen == null) {
            throw new NegocioException("Formato de imagen no soportado. Usa PNG, JPG, GIF o BMP.");
        }
        BufferedImage reducida = reducir(imagen);
        String base = (codigoProducto == null || codigoProducto.isBlank() ? "producto" : codigoProducto)
                .replaceAll("[^A-Za-z0-9_-]", "_");
        String nombre = base + "-" + System.currentTimeMillis() + ".png";
        try {
            Files.createDirectories(carpeta);
            ImageIO.write(reducida, "png", carpeta.resolve(nombre).toFile());
        } catch (IOException e) {
            throw new NegocioException("No se pudo guardar la imagen: " + e.getMessage());
        }
        log.info("Imagen de producto guardada: {}", nombre);
        return nombre;
    }

    /** Archivo de una imagen guardada, si existe. */
    public Optional<Path> ruta(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        Path p = carpeta.resolve(nombre);
        return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    public void eliminar(String nombre) {
        ruta(nombre).ifPresent(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("No se pudo borrar la imagen {}: {}", p, e.getMessage());
            }
        });
    }

    private static BufferedImage reducir(BufferedImage img) {
        int ancho = img.getWidth();
        int alto = img.getHeight();
        double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(ancho, alto));
        int w = Math.max(1, (int) Math.round(ancho * escala));
        int h = Math.max(1, (int) Math.round(alto * escala));
        BufferedImage salida = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = salida.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return salida;
    }
}
