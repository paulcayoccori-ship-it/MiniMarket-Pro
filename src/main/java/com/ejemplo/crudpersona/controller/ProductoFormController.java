package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.exception.NegocioException;
import com.ejemplo.crudpersona.model.Categoria;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.UnidadMedida;
import com.ejemplo.crudpersona.service.ImagenProductoService;
import com.ejemplo.crudpersona.service.InventarioService;
import com.ejemplo.crudpersona.service.ProductoService;
import com.ejemplo.crudpersona.service.UnidadMedidaService;
import com.ejemplo.crudpersona.ui.Formularios;
import com.ejemplo.crudpersona.ui.Notificador;
import com.ejemplo.crudpersona.ui.Tablas;
import jakarta.validation.ConstraintViolationException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Formulario de alta y edicion de productos en el panel lateral.
 * <ul>
 *   <li>Con codigo de barras (escaneado o escrito) o a granel / sin codigo (codigo interno automatico).</li>
 *   <li>Unidad de venta: Unidad, Kilo y las que cree el usuario (Six pack, Caja...).
 *       A granel solo admite Unidad o unidades con decimales (Kilo).</li>
 *   <li>Categoria con creacion en el momento, precio de compra / venta, stock inicial e imagen opcional.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ProductoFormController {

    /** Marca de importe mal escrito (se compara por referencia). */
    private static final BigDecimal INVALIDO = new BigDecimal("-1");
    private static final Pattern CANTIDAD_DECIMAL = Pattern.compile("\\d{0,6}([.,]\\d{0,3})?");

    private final ProductoService service;
    private final UnidadMedidaService unidadService;
    private final ImagenProductoService imagenService;
    private final Notificador notificador;

    @FXML private Label lblTitulo;
    @FXML private Label lblSubtitulo;
    @FXML private ToggleGroup grupoTipo;
    @FXML private ToggleButton tglConCodigo;
    @FXML private ToggleButton tglGranel;
    @FXML private Label lblCodigo;
    @FXML private TextField txtCodigo;
    @FXML private Label lblAyudaCodigo;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<Categoria> cmbCategoria;
    @FXML private Button btnNuevaCategoria;
    @FXML private HBox panelNuevaCategoria;
    @FXML private TextField txtNuevaCategoria;
    @FXML private Label lblUnidad;
    @FXML private ComboBox<UnidadMedida> cmbUnidad;
    @FXML private Button btnNuevaUnidad;
    @FXML private VBox panelNuevaUnidad;
    @FXML private TextField txtNuevaUnidad;
    @FXML private TextField txtNuevaAbreviatura;
    @FXML private CheckBox chkNuevaDecimales;
    @FXML private Label lblAyudaUnidad;
    @FXML private Label lblPrecioCompra;
    @FXML private Label lblPrecioVenta;
    @FXML private TextField txtPrecioCompra;
    @FXML private TextField txtPrecioVenta;
    @FXML private Label lblGanancia;
    @FXML private VBox campoStockInicial;
    @FXML private TextField txtStockInicial;
    @FXML private VBox campoStockActual;
    @FXML private Label lblStockActual;
    @FXML private TextField txtStockMinimo;
    @FXML private Label lblAyudaStock;
    @FXML private ImageView vistaImagen;
    @FXML private FontIcon iconoSinImagen;
    @FXML private Button btnQuitarImagen;
    @FXML private Label lblImagen;
    @FXML private TextArea txtDescripcion;

    private Parent panel;
    private Long idEditando;
    /** Codigo que tenia el producto al abrir la edicion (para no regenerarlo). */
    private String codigoOriginal;
    private boolean granelOriginal;
    private Consumer<Producto> alGuardar;

    /** Todas las unidades; el combo muestra solo las que aplican al tipo elegido. */
    private List<UnidadMedida> unidades = List.of();
    /** Imagen guardada que tenia el producto al abrir el formulario. */
    private String imagenOriginal;
    /** Archivo elegido en esta edicion (aun no copiado). */
    private File imagenElegida;
    private boolean imagenQuitada;

    /** Abre el formulario vacio para registrar un producto nuevo. */
    public void nuevo(Consumer<Producto> alGuardar) {
        abrir(null, alGuardar);
    }

    /** Abre el formulario con los datos de {@code producto} para editarlo. */
    public void editar(Producto producto, Consumer<Producto> alGuardar) {
        abrir(producto, alGuardar);
    }

    private void abrir(Producto p, Consumer<Producto> alGuardar) {
        cargarVista();
        this.alGuardar = alGuardar;
        limpiar();
        cargarCategorias();
        unidades = unidadService.listar();

        if (p == null) {
            idEditando = null;
            codigoOriginal = null;
            granelOriginal = false;
            imagenOriginal = null;
            lblTitulo.setText("Nuevo producto");
            lblSubtitulo.setText("Agrega un producto al catalogo");
            tglConCodigo.setSelected(true);
            aplicarTipo();
            mostrarStockInicial(true);
        } else {
            idEditando = p.getId();
            codigoOriginal = p.getCodigoProducto();
            granelOriginal = p.isAGranel();
            imagenOriginal = p.getImagen();
            lblTitulo.setText("Editar producto");
            lblSubtitulo.setText("Registro #" + p.getId() + " · " + p.getNombre());
            (p.isAGranel() ? tglGranel : tglConCodigo).setSelected(true);
            aplicarTipo();
            seleccionarUnidad(p.getUnidad());
            txtNombre.setText(p.getNombre());
            txtDescripcion.setText(p.getDescripcion() == null ? "" : p.getDescripcion());
            txtPrecioVenta.setText(p.getPrecio() == null ? "" : p.getPrecio().toPlainString());
            txtPrecioCompra.setText(p.getPrecioCompra() == null ? "" : p.getPrecioCompra().toPlainString());
            seleccionarCategoria(p.getCategoria());
            txtStockMinimo.setText(String.valueOf(p.getStockMinimo()));
            lblStockActual.setText(InventarioService.texto(p.getStock()) + " " + p.getAbreviaturaUnidad());
            mostrarStockInicial(false);
        }
        mostrarImagenActual();
        actualizarGanancia();
        notificador.abrirPanel(panel);
        Platform.runLater(() -> (tglGranel.isSelected() ? txtNombre : txtCodigo).requestFocus());
    }

    @FXML
    public void initialize() {
        // Siempre debe quedar un tipo elegido.
        grupoTipo.selectedToggleProperty().addListener((obs, anterior, actual) -> {
            if (actual == null) {
                grupoTipo.selectToggle(anterior);
                return;
            }
            aplicarTipo();
        });

        cmbCategoria.setCellFactory(lv -> new CeldaCategoria());
        cmbCategoria.setButtonCell(new CeldaCategoria());
        cmbUnidad.valueProperty().addListener((o, a, b) -> aplicarUnidad());

        Formularios.soloDecimal(txtPrecioCompra);
        Formularios.soloDecimal(txtPrecioVenta);
        txtStockInicial.setTextFormatter(new TextFormatter<>(c ->
                CANTIDAD_DECIMAL.matcher(c.getControlNewText()).matches() ? c : null));
        Formularios.soloDigitos(txtStockMinimo, 5);
        Formularios.limpiarAlEditar(txtCodigo, txtNombre, txtPrecioCompra, txtPrecioVenta,
                txtStockInicial, txtStockMinimo, txtDescripcion, txtNuevaCategoria, txtNuevaUnidad);
        txtPrecioCompra.textProperty().addListener((o, a, b) -> actualizarGanancia());
        txtPrecioVenta.textProperty().addListener((o, a, b) -> actualizarGanancia());

        // El lector de codigo de barras "escribe" el numero y envia Enter: se valida el
        // codigo y se pasa al nombre, sin guardar el formulario por accidente.
        txtCodigo.setOnAction(e -> {
            e.consume();
            if (validarCodigoDisponible()) {
                txtNombre.requestFocus();
            }
        });
        txtNombre.setOnAction(e -> cmbCategoria.requestFocus());
        txtPrecioCompra.setOnAction(e -> txtPrecioVenta.requestFocus());
        txtPrecioVenta.setOnAction(e -> onGuardar());
        txtNuevaCategoria.setOnAction(e -> onCrearCategoria());
        txtNuevaCategoria.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                onOcultarNuevaCategoria();
            }
        });
        txtNuevaUnidad.setOnAction(e -> txtNuevaAbreviatura.requestFocus());
        txtNuevaAbreviatura.setOnAction(e -> onCrearUnidad());
    }

    // -------------------------------------------------------------- acciones

    @FXML
    private void onCerrar() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onMostrarNuevaCategoria() {
        mostrar(panelNuevaCategoria, true);
        btnNuevaCategoria.setDisable(true);
        txtNuevaCategoria.clear();
        txtNuevaCategoria.requestFocus();
    }

    @FXML
    private void onOcultarNuevaCategoria() {
        Formularios.limpiarError(txtNuevaCategoria);
        mostrar(panelNuevaCategoria, false);
        btnNuevaCategoria.setDisable(false);
    }

    @FXML
    private void onCrearCategoria() {
        try {
            Categoria nueva = service.crearCategoria(txtNuevaCategoria.getText());
            cmbCategoria.getItems().add(nueva);
            cmbCategoria.setValue(nueva);
            onOcultarNuevaCategoria();
            notificador.exito("Categoria \"" + nueva.getNombre() + "\" creada.");
            cmbUnidad.requestFocus();
        } catch (NegocioException e) {
            Formularios.marcarError(txtNuevaCategoria, e.getMessage());
        }
    }

    @FXML
    private void onMostrarNuevaUnidad() {
        mostrar(panelNuevaUnidad, true);
        btnNuevaUnidad.setDisable(true);
        txtNuevaUnidad.clear();
        txtNuevaAbreviatura.clear();
        chkNuevaDecimales.setSelected(tglGranel.isSelected());
        chkNuevaDecimales.setDisable(tglGranel.isSelected());
        txtNuevaUnidad.requestFocus();
    }

    @FXML
    private void onOcultarNuevaUnidad() {
        Formularios.limpiarError(txtNuevaUnidad);
        mostrar(panelNuevaUnidad, false);
        btnNuevaUnidad.setDisable(false);
    }

    @FXML
    private void onCrearUnidad() {
        try {
            UnidadMedida nueva = unidadService.crear(txtNuevaUnidad.getText(), txtNuevaAbreviatura.getText(),
                    tglGranel.isSelected() || chkNuevaDecimales.isSelected());
            unidades = unidadService.listar();
            filtrarUnidades();
            seleccionarUnidad(nueva);
            onOcultarNuevaUnidad();
            notificador.exito("Unidad \"" + nueva.getNombre() + "\" creada.");
            txtPrecioCompra.requestFocus();
        } catch (NegocioException e) {
            Formularios.marcarError(txtNuevaUnidad, e.getMessage());
        }
    }

    @FXML
    private void onElegirImagen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elegir imagen del producto");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imagenes (PNG, JPG, GIF, BMP)", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File archivo = chooser.showOpenDialog(panel.getScene().getWindow());
        if (archivo == null) {
            return;
        }
        Image previa = new Image(archivo.toURI().toString(), 240, 240, true, true);
        if (previa.isError()) {
            notificador.advertencia("No se pudo abrir esa imagen. Prueba con PNG o JPG.");
            return;
        }
        imagenElegida = archivo;
        imagenQuitada = false;
        ponerImagen(previa, archivo.getName());
    }

    @FXML
    private void onQuitarImagen() {
        imagenElegida = null;
        imagenQuitada = true;
        ponerImagen(null, null);
    }

    @FXML
    private void onGuardar() {
        Formularios.limpiarErrores(txtCodigo, txtNombre, txtPrecioCompra, txtPrecioVenta,
                txtStockInicial, txtStockMinimo, txtDescripcion);

        if (!tglGranel.isSelected() && txtCodigo.getText().isBlank()) {
            Formularios.marcarError(txtCodigo, "Escanea o escribe el codigo de barras");
            txtCodigo.requestFocus();
            return;
        }
        if (!validarCodigoDisponible()) {
            return;
        }
        BigDecimal venta = leerImporte(txtPrecioVenta, "El precio de venta");
        BigDecimal compra = leerImporte(txtPrecioCompra, "El precio de compra");
        if (venta == INVALIDO || compra == INVALIDO) {
            return;
        }
        if (venta == null || venta.signum() <= 0) {
            Formularios.marcarError(txtPrecioVenta, "Indica a cuanto se vendera (mayor a cero)");
            return;
        }
        UnidadMedida unidad = cmbUnidad.getValue() == null ? unidadService.unidad() : cmbUnidad.getValue();
        BigDecimal stockInicial = BigDecimal.ZERO;
        String textoStock = txtStockInicial.getText().trim().replace(",", ".");
        if (!textoStock.isEmpty() && !textoStock.equals(".")) {
            stockInicial = new BigDecimal(textoStock);
            if (!unidad.isDecimales() && stockInicial.stripTrailingZeros().scale() > 0) {
                Formularios.marcarError(txtStockInicial, "En " + unidad.getNombre() + " el stock es un numero entero");
                return;
            }
        }

        Producto producto = Producto.builder()
                .id(idEditando)
                .nombre(txtNombre.getText().trim())
                .codigoProducto(txtCodigo.getText().trim())
                .descripcion(txtDescripcion.getText().trim())
                .precio(venta)
                .precioCompra(compra)
                .granel(tglGranel.isSelected())
                .unidad(unidad)
                .imagen(imagenOriginal)
                .categoria(cmbCategoria.getValue())
                .stockMinimo(txtStockMinimo.getText().isBlank() ? 5 : Integer.parseInt(txtStockMinimo.getText()))
                .build();

        try {
            // La imagen se copia solo cuando el resto de datos es valido.
            String imagenNueva = null;
            if (imagenElegida != null) {
                imagenNueva = imagenService.guardar(imagenElegida, producto.getCodigoProducto());
                producto.setImagen(imagenNueva);
            } else if (imagenQuitada) {
                producto.setImagen(null);
            }
            boolean esNuevo = idEditando == null;
            Producto guardado;
            try {
                guardado = service.guardar(producto, esNuevo ? stockInicial : BigDecimal.ZERO);
            } catch (RuntimeException e) {
                if (imagenNueva != null) {
                    imagenService.eliminar(imagenNueva);
                }
                throw e;
            }
            if (imagenOriginal != null && !imagenOriginal.equals(guardado.getImagen())) {
                imagenService.eliminar(imagenOriginal);
            }
            guardado.setCategoria(cmbCategoria.getValue());
            notificador.cerrarPanel();
            notificador.exito(esNuevo
                    ? "Producto \"" + guardado.getNombre() + "\" registrado con codigo " + guardado.getCodigoProducto() + "."
                    : "Producto \"" + guardado.getNombre() + "\" actualizado.");
            if (alGuardar != null) {
                alGuardar.accept(guardado);
            }
        } catch (ConstraintViolationException e) {
            List<String> otros = Formularios.mostrarViolaciones(e, Map.of(
                    "nombre", txtNombre, "codigoProducto", txtCodigo, "descripcion", txtDescripcion,
                    "precio", txtPrecioVenta, "precioCompra", txtPrecioCompra, "stockMinimo", txtStockMinimo));
            otros.forEach(notificador::advertencia);
        } catch (NegocioException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("imagen")) {
                notificador.advertencia(e.getMessage());
            } else {
                Formularios.marcarError(txtCodigo, e.getMessage());
            }
        } catch (Exception e) {
            notificador.error("Error inesperado: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------- apoyo

    private void aplicarTipo() {
        boolean granel = tglGranel.isSelected();
        txtCodigo.setEditable(!granel);
        txtCodigo.setFocusTraversable(!granel);
        Formularios.limpiarError(txtCodigo);
        if (granel) {
            lblCodigo.setText("Codigo interno (automatico)");
            lblAyudaCodigo.setText("Los productos a granel o sin codigo se eligen tocandolos en el catalogo "
                    + "del punto de venta o buscandolos por nombre.");
            // Al editar un granel se conserva su codigo; si antes tenia codigo de barras, se genera uno nuevo.
            txtCodigo.setText(idEditando != null && granelOriginal ? codigoOriginal : service.siguienteCodigoGranel());
        } else {
            lblCodigo.setText("Codigo de barras *");
            lblAyudaCodigo.setText("Con el lector: haz clic aqui y escanea. A mano: escribe el numero y presiona Enter.");
            boolean teniaCodigoReal = idEditando != null && !granelOriginal;
            txtCodigo.setText(teniaCodigoReal ? codigoOriginal : "");
        }
        txtCodigo.getStyleClass().remove("pf-codigo-auto");
        if (granel) {
            txtCodigo.getStyleClass().add("pf-codigo-auto");
        }
        UnidadMedida anterior = cmbUnidad.getValue();
        filtrarUnidades();
        seleccionarUnidad(anterior);
        if (cmbUnidad.getValue() == null) {
            // Por defecto: a granel se vende por kilo; con codigo, por unidad.
            seleccionarUnidad(granel ? unidadService.kilo() : unidadService.unidad());
        }
        if (panelNuevaUnidad.isVisible()) {
            chkNuevaDecimales.setSelected(granel || chkNuevaDecimales.isSelected());
            chkNuevaDecimales.setDisable(granel);
        }
    }

    /** A granel: Unidad o unidades con decimales (Kilo...). Con codigo: todas. */
    private void filtrarUnidades() {
        boolean granel = tglGranel.isSelected();
        cmbUnidad.setItems(FXCollections.observableArrayList(unidades.stream()
                .filter(u -> !granel || u.isDecimales() || UnidadMedida.ABREV_UNIDAD.equalsIgnoreCase(u.getAbreviatura()))
                .toList()));
        lblAyudaUnidad.setText(granel
                ? "A granel: por Kilo (acepta 0.250, 1.5...) o por Unidad. Con \"Nueva\" puedes crear Litro, etc."
                : "Unidad, Six pack, Caja... Con \"Nueva\" creas las que necesites.");
    }

    private void seleccionarUnidad(UnidadMedida u) {
        cmbUnidad.setValue(u == null ? null : cmbUnidad.getItems().stream()
                .filter(x -> x.getId().equals(u.getId())).findFirst().orElse(null));
    }

    private void aplicarUnidad() {
        UnidadMedida u = cmbUnidad.getValue();
        String abrev = u == null ? UnidadMedida.ABREV_UNIDAD : u.getAbreviatura();
        lblPrecioCompra.setText("Precio de compra (x " + abrev + ")");
        lblPrecioVenta.setText("Precio de venta (x " + abrev + ") *");
        txtStockInicial.setPromptText(u != null && u.isDecimales() ? "0.000 " + abrev : "0 " + abrev);
        actualizarGanancia();
    }

    /** Avisa al instante si el codigo escaneado ya pertenece a otro producto. */
    private boolean validarCodigoDisponible() {
        String codigo = txtCodigo.getText().trim();
        if (codigo.isEmpty()) {
            return true;
        }
        var existente = service.buscarPorCodigo(codigo);
        if (existente.isPresent() && !existente.get().getId().equals(idEditando)) {
            Formularios.marcarError(txtCodigo, "Este codigo ya es de \"" + existente.get().getNombre() + "\"");
            txtCodigo.selectAll();
            txtCodigo.requestFocus();
            return false;
        }
        return true;
    }

    /** Devuelve el importe, {@code null} si esta vacio o INVALIDO (marcando el error) si no es valido. */
    private static BigDecimal leerImporte(TextField campo, String nombre) {
        String texto = campo.getText().trim().replace(",", ".");
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(texto).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            Formularios.marcarError(campo, nombre + " debe ser un numero valido");
            return INVALIDO;
        }
    }

    private void actualizarGanancia() {
        BigDecimal compra = numero(txtPrecioCompra.getText());
        BigDecimal venta = numero(txtPrecioVenta.getText());
        lblGanancia.getStyleClass().remove("negativa");
        if (compra == null || venta == null || compra.signum() <= 0) {
            lblGanancia.setText("Indica ambos precios para ver la ganancia.");
            return;
        }
        String abrev = cmbUnidad.getValue() == null ? UnidadMedida.ABREV_UNIDAD : cmbUnidad.getValue().getAbreviatura();
        BigDecimal ganancia = venta.subtract(compra);
        BigDecimal margen = ganancia.multiply(BigDecimal.valueOf(100)).divide(compra, 1, RoundingMode.HALF_UP);
        lblGanancia.setText((ganancia.signum() < 0 ? "Perdida: " : "Ganancia: ")
                + Tablas.MONEDA.format(ganancia.abs()) + " por " + abrev + " (" + margen + "%)");
        if (ganancia.signum() < 0) {
            lblGanancia.getStyleClass().add("negativa");
        }
    }

    private static BigDecimal numero(String texto) {
        try {
            return texto == null || texto.isBlank() ? null : new BigDecimal(texto.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Muestra la foto subida del producto o, si no tiene, la de la carpeta de recursos por codigo. */
    private void mostrarImagenActual() {
        Image img = null;
        String nombre = null;
        var ruta = imagenService.ruta(imagenOriginal);
        if (ruta.isPresent()) {
            img = new Image(ruta.get().toUri().toString(), 240, 240, true, true);
            nombre = "Imagen actual";
        } else if (codigoOriginal != null) {
            for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
                URL url = getClass().getResource("/productos/" + codigoOriginal + ext);
                if (url != null) {
                    img = new Image(url.toExternalForm(), 240, 240, true, true);
                    nombre = "Imagen de catalogo";
                    break;
                }
            }
        }
        ponerImagen(img, nombre);
        btnQuitarImagen.setDisable(imagenOriginal == null);
    }

    private void ponerImagen(Image img, String descripcion) {
        vistaImagen.setImage(img);
        iconoSinImagen.setVisible(img == null);
        btnQuitarImagen.setDisable(img == null);
        lblImagen.setText(img == null
                ? "Sin imagen. PNG, JPG, GIF o BMP; se muestra a color en el punto de venta."
                : descripcion);
    }

    private void mostrarStockInicial(boolean nuevo) {
        mostrar(campoStockInicial, nuevo);
        mostrar(campoStockActual, !nuevo);
        lblAyudaStock.setText(nuevo
                ? "El stock inicial queda registrado en el kardex. Luego cambia con compras y ventas."
                : "El stock solo cambia al registrar compras y ventas (ver Inventario).");
    }

    private void cargarCategorias() {
        ObservableList<Categoria> categorias = FXCollections.observableArrayList();
        categorias.add(null);
        categorias.addAll(service.listarCategorias());
        cmbCategoria.setItems(categorias);
    }

    private void seleccionarCategoria(Categoria c) {
        cmbCategoria.setValue(c == null ? null : cmbCategoria.getItems().stream()
                .filter(x -> x != null && x.getId().equals(c.getId())).findFirst().orElse(null));
    }

    private void limpiar() {
        Formularios.limpiarErrores(txtCodigo, txtNombre, txtPrecioCompra, txtPrecioVenta,
                txtStockInicial, txtStockMinimo, txtDescripcion);
        onOcultarNuevaCategoria();
        onOcultarNuevaUnidad();
        txtCodigo.clear();
        txtNombre.clear();
        txtDescripcion.clear();
        txtPrecioCompra.clear();
        txtPrecioVenta.clear();
        txtStockInicial.clear();
        txtStockMinimo.setText("5");
        lblStockActual.setText("0");
        cmbCategoria.setValue(null);
        cmbUnidad.setValue(null);
        imagenElegida = null;
        imagenQuitada = false;
    }

    private static void mostrar(javafx.scene.Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private void cargarVista() {
        if (panel != null) {
            return;
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/producto-form.fxml"));
        loader.setController(this);
        try {
            panel = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar el formulario de producto", e);
        }
    }

    private static class CeldaCategoria extends ListCell<Categoria> {
        @Override
        protected void updateItem(Categoria categoria, boolean vacio) {
            super.updateItem(categoria, vacio);
            setText(vacio ? null : categoria == null ? "Sin categoria" : categoria.getNombre());
        }
    }
}
