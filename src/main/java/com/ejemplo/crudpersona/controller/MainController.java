package com.ejemplo.crudpersona.controller;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.util.Animations;
import com.ejemplo.crudpersona.ui.Notificador;
import com.ejemplo.crudpersona.ui.Refrescable;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MainController {

    private static final double ANCHO_EXPANDIDO = 248;
    private static final double ANCHO_CONTRAIDO = 76;
    private static final Duration DURACION_MENU = Duration.millis(260);

    private final ApplicationContext context;
    private final Notificador notificador;

    @FXML private StackPane raiz;
    @FXML private VBox sidebar;
    @FXML private StackPane contenido;
    @FXML private ModalPane modalPanel;
    @FXML private ModalPane modalDialogo;
    @FXML private VBox zonaNotificaciones;

    @FXML private ToggleGroup menu;
    @FXML private ToggleButton navDashboard;
    @FXML private ToggleButton navPersonas;
    @FXML private ToggleButton navCategorias;
    @FXML private ToggleButton navProductos;
    @FXML private ToggleButton navPuntoVenta;
    @FXML private ToggleButton navVentas;
    @FXML private ToggleButton navCompra;
    @FXML private ToggleButton navCompras;
    @FXML private ToggleButton navInventario;
    @FXML private Region indicador;

    @FXML private VBox marcaTextos;
    @FXML private Button btnTema;
    @FXML private Button btnContraer;

    /** Vistas ya cargadas: se construyen una sola vez y se reutilizan. */
    private final Map<String, Vista> vistas = new HashMap<>();
    private boolean oscuro = false;
    private boolean contraido = false;

    private record Vista(Parent nodo, Object controlador) {}

    @FXML
    public void initialize() {
        notificador.registrar(modalPanel, modalDialogo, zonaNotificaciones);
        modalPanel.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        modalDialogo.setAlignment(javafx.geometry.Pos.CENTER);

        navDashboard.setUserData("dashboard-view");
        navPersonas.setUserData("persona-view");
        navCategorias.setUserData("categoria-view");
        navProductos.setUserData("producto-view");
        navPuntoVenta.setUserData("punto-venta-view");
        navVentas.setUserData("ventas-view");
        navCompra.setUserData("compra-form-view");
        navCompras.setUserData("compras-view");
        navInventario.setUserData("inventario-view");
        for (ToggleButton b : botonesMenu()) {
            Tooltip t = new Tooltip(b.getText());
            t.setShowDelay(Duration.millis(300));
            b.setTooltip(t);
        }

        // Siempre debe haber una opcion seleccionada.
        menu.selectedToggleProperty().addListener((obs, anterior, actual) -> {
            if (actual == null) {
                menu.selectToggle(anterior);
                return;
            }
            mostrar((String) actual.getUserData());
            moverIndicador(actual);
        });

        menu.selectToggle(navDashboard);
        Platform.runLater(() -> moverIndicador(navDashboard));
    }

    // ----------------------------------------------------------- navegacion

    /** Cambia a otra vista como si se hubiera pulsado su opcion en el menu lateral. */
    public void irA(String vista) {
        botonesMenu().stream()
                .filter(b -> vista.equals(b.getUserData()))
                .findFirst()
                .ifPresent(menu::selectToggle);
    }

    private void mostrar(String nombre) {
        Vista vista = vistas.computeIfAbsent(nombre, this::cargar);
        contenido.getChildren().setAll(vista.nodo());
        if (vista.controlador() instanceof Refrescable r) {
            r.refrescar();
        }
        Animations.fadeInUp(vista.nodo(), Duration.millis(380)).play();
    }

    private Vista cargar(String nombre) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/" + nombre + ".fxml"));
        loader.setControllerFactory(context::getBean);
        // Explicito: en macOS el hilo de eventos puede no tener class loader de contexto.
        loader.setClassLoader(getClass().getClassLoader());
        try {
            Parent nodo = loader.load();
            return new Vista(nodo, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la vista " + nombre, e);
        }
    }

    /** Desliza el fondo resaltado hasta la opcion seleccionada. */
    private void moverIndicador(Toggle toggle) {
        Node boton = (Node) toggle;
        double destino = boton.getBoundsInParent().getMinY();
        indicador.setPrefHeight(boton.getBoundsInParent().getHeight());
        new Timeline(new KeyFrame(Duration.millis(280),
                new KeyValue(indicador.translateYProperty(), destino, Interpolator.SPLINE(0.4, 0, 0.2, 1))))
                .play();
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onCambiarTema() {
        oscuro = !oscuro;
        Application.setUserAgentStylesheet(oscuro
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        // Tema blanco y negro: el modo oscuro agrega su hoja de grises.
        String hojaOscura = java.util.Objects.requireNonNull(
                getClass().getResource("/css/bn-oscuro.css")).toExternalForm();
        if (oscuro) {
            raiz.getStyleClass().add("dark");
            if (!raiz.getScene().getStylesheets().contains(hojaOscura)) {
                raiz.getScene().getStylesheets().add(hojaOscura);
            }
        } else {
            raiz.getStyleClass().remove("dark");
            raiz.getScene().getStylesheets().remove(hojaOscura);
        }
        ((FontIcon) btnTema.getGraphic()).setIconCode(oscuro ? Feather.SUN : Feather.MOON);
        btnTema.setText(oscuro ? "Modo claro" : "Modo oscuro");
        Animations.fadeIn(contenido, Duration.millis(300)).play();
    }

    @FXML
    private void onContraerMenu() {
        contraido = !contraido;
        double ancho = contraido ? ANCHO_CONTRAIDO : ANCHO_EXPANDIDO;

        Timeline ajuste = new Timeline(new KeyFrame(DURACION_MENU,
                new KeyValue(sidebar.prefWidthProperty(), ancho, Interpolator.SPLINE(0.4, 0, 0.2, 1)),
                new KeyValue(sidebar.minWidthProperty(), ancho, Interpolator.SPLINE(0.4, 0, 0.2, 1))));

        ContentDisplay modo = contraido ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT;
        List<Node> textos = new ArrayList<>(sidebar.lookupAll(".nav-seccion"));
        textos.add(marcaTextos);

        if (contraido) {
            textos.forEach(n -> { n.setVisible(false); n.setManaged(false); });
            todosLosBotones().forEach(b -> b.setContentDisplay(modo));
            ajuste.play();
        } else {
            ajuste.setOnFinished(e -> {
                textos.forEach(n -> { n.setVisible(true); n.setManaged(true); });
                todosLosBotones().forEach(b -> b.setContentDisplay(modo));
                ParallelTransition aparicion = new ParallelTransition();
                textos.forEach(n -> aparicion.getChildren().add(Animations.fadeIn(n, Duration.millis(200))));
                aparicion.play();
                Platform.runLater(() -> moverIndicador(menu.getSelectedToggle()));
            });
            ajuste.play();
        }
        ((FontIcon) btnContraer.getGraphic()).setIconCode(
                contraido ? Feather.CHEVRONS_RIGHT : Feather.CHEVRONS_LEFT);
        Platform.runLater(() -> moverIndicador(menu.getSelectedToggle()));
    }

    private List<ToggleButton> botonesMenu() {
        return List.of(navDashboard, navPuntoVenta, navVentas, navCompra, navCompras, navInventario,
                navPersonas, navCategorias, navProductos);
    }

    private List<ButtonBase> todosLosBotones() {
        List<ButtonBase> todos = new ArrayList<>(botonesMenu());
        todos.add(btnTema);
        todos.add(btnContraer);
        return todos;
    }
}
