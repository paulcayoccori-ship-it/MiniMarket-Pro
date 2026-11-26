package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.model.Producto;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;

/** Configuracion visual comun de las tablas de las vistas CRUD. */
public final class Tablas {

    public static final Locale LOCALE = Locale.forLanguageTag("es-PE");
    public static final NumberFormat MONEDA = NumberFormat.getCurrencyInstance(LOCALE);

    private Tablas() {
    }

    /** Estilo base, columnas ajustadas al ancho, estado vacio y doble clic para editar. */
    public static <T> void configurar(TableView<T> tabla, String textoVacio, Consumer<T> alDobleClic) {
        configurar(tabla, Feather.INBOX, "Sin resultados", textoVacio, alDobleClic);
    }

    /** Igual que {@link #configurar(TableView, String, Consumer)} con icono y titulo propios para el estado vacio. */
    public static <T> void configurar(TableView<T> tabla, Ikon iconoVacio, String tituloVacio, String textoVacio,
                                      Consumer<T> alDobleClic) {
        tabla.getStyleClass().add("tabla");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        FontIcon icono = new FontIcon(iconoVacio);
        icono.getStyleClass().add("vacio-icono");
        Label titulo = new Label(tituloVacio);
        titulo.getStyleClass().add("vacio-titulo");
        Label detalle = new Label(textoVacio);
        detalle.getStyleClass().add("vacio-detalle");
        VBox vacio = new VBox(8, icono, titulo, detalle);
        vacio.setAlignment(Pos.CENTER);
        tabla.setPlaceholder(vacio);

        tabla.setRowFactory(tv -> {
            TableRow<T> fila = new TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    alDobleClic.accept(fila.getItem());
                }
            });
            return fila;
        });
    }

    /** Celda "#12" en gris. */
    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaId() {
        return col -> new TableCell<>() {
            {
                getStyleClass().add("celda-id");
            }

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : "#" + valor);
            }
        };
    }

    /** Celda con avatar de iniciales seguido del texto. */
    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaAvatar() {
        return col -> new TableCell<>() {
            private final Label iniciales = new Label();
            private final StackPane avatar = new StackPane(iniciales);
            private final Label nombre = new Label();
            private final HBox caja = new HBox(10, avatar, nombre);

            {
                avatar.getStyleClass().add("avatar");
                caja.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio || valor == null) {
                    setGraphic(null);
                    return;
                }
                iniciales.setText(iniciales(valor));
                nombre.setText(valor);
                setGraphic(caja);
            }
        };
    }

    /** Celda con el texto dentro de una "pastilla" de color. */
    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaChip(String textoVacio) {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio) {
                    setGraphic(null);
                    return;
                }
                boolean sinValor = valor == null || valor.isBlank();
                chip.setText(sinValor ? textoVacio : valor);
                chip.getStyleClass().setAll("chip");
                if (sinValor) {
                    chip.getStyleClass().add("neutro");
                }
                setGraphic(chip);
            }
        };
    }

    /** Celda de importe alineada a la derecha. */
    public static <T> Callback<TableColumn<T, BigDecimal>, TableCell<T, BigDecimal>> celdaMoneda() {
        return col -> new TableCell<>() {
            {
                getStyleClass().add("celda-importe");
            }

            @Override
            protected void updateItem(BigDecimal valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || valor == null ? null : MONEDA.format(valor));
            }
        };
    }

    /** Texto gris para valores opcionales vacios. */
    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaOpcional() {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                boolean sinValor = !vacio && (valor == null || valor.isBlank());
                setText(vacio ? null : sinValor ? "—" : valor);
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("sin-valor"), sinValor);
            }
        };
    }

    /** Celda de estado de venta: pastilla verde (EMITIDA) o roja (ANULADA). */
    public static <T> Callback<TableColumn<T, String>, TableCell<T, String>> celdaEstado() {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(String valor, boolean vacio) {
                super.updateItem(valor, vacio);
                if (vacio || valor == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(valor);
                chip.getStyleClass().setAll("chip", "ANULADA".equals(valor) ? "peligro" : "exito");
                setGraphic(chip);
            }
        };
    }

    /** Stock en una pastilla: roja sin stock, ambar si esta en o bajo el minimo, verde si alcanza. */
    public static <T> Callback<TableColumn<T, Producto>, TableCell<T, Producto>> celdaStock() {
        return col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(Producto p, boolean vacio) {
                super.updateItem(p, vacio);
                if (vacio || p == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(p.isSinStock() ? "Sin stock"
                        : p.getStock().stripTrailingZeros().toPlainString() + " " + p.getAbreviaturaUnidad());
                chip.getStyleClass().setAll("chip", claseStock(p));
                setGraphic(chip);
            }
        };
    }

    public static String claseStock(Producto p) {
        return p.isSinStock() ? "peligro" : p.isStockBajo() ? "advertencia" : "exito";
    }

    /** Hasta dos iniciales en mayuscula: "Maria Quispe Rojas" → "MQ". */
    public static String iniciales(String texto) {
        String[] partes = texto.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length && sb.length() < 2; i++) {
            if (!partes[i].isEmpty()) {
                sb.append(Character.toUpperCase(partes[i].charAt(0)));
            }
        }
        return sb.toString();
    }
}
