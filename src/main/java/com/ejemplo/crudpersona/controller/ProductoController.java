package com.ejemplo.crudpersona.controller;

import atlantafx.base.util.Animations;
import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.Categoria;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.service.ProductoService;
import com.ejemplo.crudpersona.ui.Formularios;
import com.ejemplo.crudpersona.ui.Notificador;
import com.ejemplo.crudpersona.ui.Paginador;
import com.ejemplo.crudpersona.ui.Refrescable;
import com.ejemplo.crudpersona.ui.Tablas;
import jakarta.validation.ConstraintViolationException;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProductoController implements Refrescable {

    private final ProductoService service;
    private final Notificador notificador;
    private final ProductoFormController formulario;

    @FXML private TextField txtBuscar;

    @FXML private TableView<Producto> tabla;
    @FXML private TableColumn<Producto, String> colId;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colCodigo;
    @FXML private TableColumn<Producto, BigDecimal> colPrecio;
    @FXML private TableColumn<Producto, BigDecimal> colPrecioCompra;
    @FXML private TableColumn<Producto, String> colTipo;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, Producto> colStock;

    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Paginador paginador;

    private final ObservableList<Producto> datos = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colId.setCellFactory(Tablas.celdaId());
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigoProducto()));
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecio()));
        colPrecio.setCellFactory(Tablas.celdaMoneda());
        colPrecioCompra.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioCompra()));
        colPrecioCompra.setCellFactory(Tablas.celdaMoneda());
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isAGranel() ? "A granel" : "Con codigo"));
        colTipo.setCellFactory(Tablas.celdaChip("—"));
        colCategoria.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCategoria() != null ? c.getValue().getCategoria().getNombre() : null));
        colCategoria.setCellFactory(Tablas.celdaChip("Sin categoria"));
        colStock.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colStock.setCellFactory(Tablas.celdaStock());

        Tablas.configurar(tabla, "Registra un producto o cambia el texto de busqueda.", this::editar);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(seleccion.isNull());
        btnEliminar.disableProperty().bind(seleccion.isNull());

        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, a, b) -> paginador.irAPrimera());

    }

    @Override
    public void refrescar() {
        cargarTabla();
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        formulario.nuevo(p -> cargarTabla());
    }

    @FXML
    private void onEditar() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Producto p) {
        if (p != null) {
            formulario.editar(p, g -> cargarTabla());
        }
    }

    @FXML
    private void onEliminar() {
        Producto seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        notificador.confirmar("¿Eliminar " + seleccionado.getNombre() + "?",
                "Esta accion no se puede deshacer. El producto con codigo "
                        + seleccionado.getCodigoProducto() + " se eliminara permanentemente.",
                "Eliminar",
                () -> {
                    try {
                        service.eliminar(seleccionado.getId());
                        notificador.exito("Producto eliminado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    // ----------------------------------------------------------------- apoyo

    private void cargarTabla() {
        Page<Producto> pagina = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }
}
