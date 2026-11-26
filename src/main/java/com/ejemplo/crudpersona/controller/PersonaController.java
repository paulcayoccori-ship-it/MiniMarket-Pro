package com.ejemplo.crudpersona.controller;

import atlantafx.base.util.Animations;
import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.Persona;
import com.ejemplo.crudpersona.service.PersonaService;
import com.ejemplo.crudpersona.ui.Formularios;
import com.ejemplo.crudpersona.ui.Notificador;
import com.ejemplo.crudpersona.ui.Paginador;
import com.ejemplo.crudpersona.ui.Refrescable;
import com.ejemplo.crudpersona.ui.Tablas;
import jakarta.validation.ConstraintViolationException;
import javafx.application.Platform;
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

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PersonaController implements Refrescable {

    private final PersonaService service;
    private final Notificador notificador;

    @FXML private VBox panelFormulario;
    @FXML private Label lblTituloFormulario;
    @FXML private Label lblSubtituloFormulario;
    @FXML private TextField txtNombre;
    @FXML private TextField txtApellido;
    @FXML private TextField txtDni;
    @FXML private TextField txtEmail;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtBuscar;

    @FXML private TableView<Persona> tabla;
    @FXML private TableColumn<Persona, String> colId;
    @FXML private TableColumn<Persona, String> colNombre;
    @FXML private TableColumn<Persona, String> colDni;
    @FXML private TableColumn<Persona, String> colEmail;
    @FXML private TableColumn<Persona, String> colTelefono;

    @FXML private Button btnEditar;
    @FXML private Button btnEliminar;
    @FXML private Paginador paginador;

    private final ObservableList<Persona> datos = FXCollections.observableArrayList();
    /** Id del registro en edicion; {@code null} cuando el formulario registra uno nuevo. */
    private Long idEditando;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colId.setCellFactory(Tablas.celdaId());
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCompleto()));
        colNombre.setCellFactory(Tablas.celdaAvatar());
        colDni.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDni()));
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        colEmail.setCellFactory(Tablas.celdaOpcional());
        colTelefono.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTelefono()));
        colTelefono.setCellFactory(Tablas.celdaOpcional());

        Tablas.configurar(tabla, "Registra una persona o cambia el texto de busqueda.", this::editar);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnEditar.disableProperty().bind(seleccion.isNull());
        btnEliminar.disableProperty().bind(seleccion.isNull());

        paginador.setOnCambio(this::cargarTabla);
        txtBuscar.textProperty().addListener((obs, a, b) -> paginador.irAPrimera());

        Formularios.soloDigitos(txtDni, 8);
        Formularios.soloDigitos(txtTelefono, 9);
        Formularios.limpiarAlEditar(txtNombre, txtApellido, txtDni, txtEmail, txtTelefono);
    }

    @Override
    public void refrescar() {
        cargarTabla();
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNuevo() {
        tabla.getSelectionModel().clearSelection();
        limpiarFormulario();
        idEditando = null;
        lblTituloFormulario.setText("Nueva persona");
        lblSubtituloFormulario.setText("Completa los datos para registrarla");
        abrirFormulario();
    }

    @FXML
    private void onEditar() {
        editar(tabla.getSelectionModel().getSelectedItem());
    }

    private void editar(Persona p) {
        if (p == null) {
            return;
        }
        limpiarFormulario();
        idEditando = p.getId();
        txtNombre.setText(p.getNombre());
        txtApellido.setText(p.getApellido());
        txtDni.setText(p.getDni());
        txtEmail.setText(textoODefecto(p.getEmail()));
        txtTelefono.setText(textoODefecto(p.getTelefono()));
        lblTituloFormulario.setText("Editar persona");
        lblSubtituloFormulario.setText("Registro #" + p.getId() + " · " + p.getNombreCompleto());
        abrirFormulario();
    }

    @FXML
    private void onCerrarFormulario() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtNombre, txtApellido, txtDni, txtEmail, txtTelefono);
        Persona persona = Persona.builder()
                .id(idEditando)
                .nombre(txtNombre.getText().trim())
                .apellido(txtApellido.getText().trim())
                .dni(txtDni.getText().trim())
                .email(txtEmail.getText().trim())
                .telefono(txtTelefono.getText().trim())
                .build();

        try {
            boolean esNuevo = persona.getId() == null;
            Persona guardada = service.guardar(persona);
            notificador.cerrarPanel();
            notificador.exito(esNuevo
                    ? guardada.getNombreCompleto() + " se registro correctamente."
                    : "Los cambios de " + guardada.getNombreCompleto() + " se guardaron.");
            cargarTabla();
        } catch (ConstraintViolationException e) {
            List<String> otros = Formularios.mostrarViolaciones(e, Map.of(
                    "nombre", txtNombre, "apellido", txtApellido, "dni", txtDni,
                    "email", txtEmail, "telefono", txtTelefono));
            otros.forEach(notificador::advertencia);
        } catch (NegocioException e) {
            Formularios.marcarError(txtDni, e.getMessage());
        } catch (Exception e) {
            notificador.error("Error inesperado: " + e.getMessage());
        }
    }

    @FXML
    private void onEliminar() {
        Persona seleccionada = tabla.getSelectionModel().getSelectedItem();
        if (seleccionada == null) {
            return;
        }
        notificador.confirmar("¿Eliminar a " + seleccionada.getNombreCompleto() + "?",
                "Esta accion no se puede deshacer. El registro con DNI "
                        + seleccionada.getDni() + " se eliminara permanentemente.",
                "Eliminar",
                () -> {
                    try {
                        service.eliminar(seleccionada.getId());
                        notificador.exito("Registro eliminado.");
                        cargarTabla();
                    } catch (NegocioException e) {
                        notificador.error(e.getMessage());
                    }
                });
    }

    // ----------------------------------------------------------------- apoyo

    private void cargarTabla() {
        Page<Persona> pagina = service.listar(txtBuscar.getText(), paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }

    private void abrirFormulario() {
        notificador.abrirPanel(panelFormulario);
        Platform.runLater(txtDni::requestFocus);
    }

    private void limpiarFormulario() {
        Formularios.limpiarErrores(txtNombre, txtApellido, txtDni, txtEmail, txtTelefono);
        txtNombre.clear();
        txtApellido.clear();
        txtDni.clear();
        txtEmail.clear();
        txtTelefono.clear();
    }

    private String textoODefecto(String valor) {
        return valor == null ? "" : valor;
    }
}
