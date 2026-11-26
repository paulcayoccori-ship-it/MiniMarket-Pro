package com.ejemplo.crudpersona.controller;

import atlantafx.base.util.Animations;
import com.ejemplo.crudpersona.model.MovimientoInventario;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.service.InventarioService;
import com.ejemplo.crudpersona.service.InventarioService.FiltroStock;
import com.ejemplo.crudpersona.service.InventarioService.ResumenInventario;
import com.ejemplo.crudpersona.service.ReporteInventarioExcelService;
import com.ejemplo.crudpersona.ui.Exportador;
import com.ejemplo.crudpersona.ui.Notificador;
import com.ejemplo.crudpersona.ui.Paginador;
import com.ejemplo.crudpersona.ui.Refrescable;
import com.ejemplo.crudpersona.ui.Tablas;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stock por producto, alertas de reposicion y kardex de movimientos. */
@Component
@RequiredArgsConstructor
public class InventarioController implements Refrescable {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InventarioService service;
    private final ReporteInventarioExcelService excelService;
    private final Exportador exportador;
    private final Notificador notificador;
    private final MainController mainController;
    private final CompraFormController compraForm;

    @FXML private Label lblResUnidades;
    @FXML private Label lblResProductos;
    @FXML private Label lblResCosto;
    @FXML private Label lblResVenta;
    @FXML private Label lblResMargen;
    @FXML private Label lblResBajo;
    @FXML private Label lblResSin;
    @FXML private VBox kpiBajo;
    @FXML private VBox kpiSin;

    @FXML private TextField txtBuscar;
    @FXML private ComboBox<FiltroStock> cmbFiltro;
    @FXML private Button btnKardex;
    @FXML private Button btnReponer;
    @FXML private Paginador paginador;

    @FXML private TableView<Producto> tabla;
    @FXML private TableColumn<Producto, String> colCodigo;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, Producto> colStock;
    @FXML private TableColumn<Producto, String> colMinimo;
    @FXML private TableColumn<Producto, BigDecimal> colCosto;
    @FXML private TableColumn<Producto, BigDecimal> colValor;
    @FXML private TableColumn<Producto, BigDecimal> colPrecio;

    @FXML private VBox panelKardex;
    @FXML private Label lblKarProducto;
    @FXML private Label lblKarSubtitulo;
    @FXML private Label lblKarStock;
    @FXML private Label lblKarCosto;
    @FXML private Label lblKarValor;
    @FXML private Label lblKarEntradas;
    @FXML private Label lblKarSalidas;
    @FXML private TableView<MovimientoInventario> tablaKardex;
    @FXML private TableColumn<MovimientoInventario, String> colKarFecha;
    @FXML private TableColumn<MovimientoInventario, MovimientoInventario> colKarTipo;
    @FXML private TableColumn<MovimientoInventario, String> colKarDocumento;
    @FXML private TableColumn<MovimientoInventario, String> colKarEntrada;
    @FXML private TableColumn<MovimientoInventario, String> colKarSalida;
    @FXML private TableColumn<MovimientoInventario, String> colKarSaldo;

    private final ObservableList<Producto> datos = FXCollections.observableArrayList();
    private Producto enKardex;

    @FXML
    public void initialize() {
        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigoProducto()));
        colCodigo.setCellFactory(Tablas.celdaChip("—"));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colCategoria.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCategoria() != null ? c.getValue().getCategoria().getNombre() : null));
        colCategoria.setCellFactory(Tablas.celdaOpcional());
        colStock.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colStock.setCellFactory(Tablas.celdaStock());
        colMinimo.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getStockMinimo())));
        colMinimo.setCellFactory(Tablas.celdaOpcional());
        colCosto.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCostoPromedio()));
        colCosto.setCellFactory(Tablas.celdaMoneda());
        colValor.setCellValueFactory(c -> new SimpleObjectProperty<>(InventarioService.valorizado(c.getValue())));
        colValor.setCellFactory(Tablas.celdaMoneda());
        colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecio()));
        colPrecio.setCellFactory(Tablas.celdaMoneda());

        Tablas.configurar(tabla, Feather.BOX, "Sin productos",
                "No hay productos para el filtro seleccionado.", this::verKardex);
        tabla.setItems(datos);

        var seleccion = tabla.getSelectionModel().selectedItemProperty();
        btnKardex.disableProperty().bind(seleccion.isNull());
        btnReponer.disableProperty().bind(seleccion.isNull());

        cmbFiltro.getItems().setAll(FiltroStock.values());
        cmbFiltro.setValue(FiltroStock.TODOS);
        cmbFiltro.valueProperty().addListener((obs, a, b) -> paginador.irAPrimera());
        txtBuscar.textProperty().addListener((obs, a, b) -> paginador.irAPrimera());
        paginador.setOnCambio(this::cargarTabla);

        // Los indicadores de alerta funcionan como accesos directos al filtro.
        kpiBajo.setOnMouseClicked(e -> cmbFiltro.setValue(FiltroStock.BAJO));
        kpiSin.setOnMouseClicked(e -> cmbFiltro.setValue(FiltroStock.SIN_STOCK));

        colKarFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFecha().format(FECHA_HORA)));
        colKarTipo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colKarTipo.setCellFactory(col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(MovimientoInventario m, boolean vacio) {
                super.updateItem(m, vacio);
                if (vacio || m == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(m.getTipo().getDescripcion());
                chip.getStyleClass().setAll("chip", m.getTipo().isEntrada() ? "exito" : "advertencia");
                setGraphic(chip);
            }
        });
        colKarDocumento.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDocumento()));
        colKarEntrada.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEntrada().signum() == 0 ? "" : "+" + InventarioService.texto(c.getValue().getEntrada())));
        colKarEntrada.getStyleClass().add("kardex-entrada");
        colKarSalida.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSalida().signum() == 0 ? "" : "−" + InventarioService.texto(c.getValue().getSalida())));
        colKarSalida.getStyleClass().add("kardex-salida");
        colKarSaldo.setCellValueFactory(c -> new SimpleStringProperty(InventarioService.texto(c.getValue().getStockResultante())));
        colKarSaldo.getStyleClass().add("kardex-saldo");
        Tablas.configurar(tablaKardex, Feather.LIST, "Sin movimientos",
                "Este producto aun no tiene compras ni ventas.", m -> { });
    }

    @Override
    public void refrescar() {
        cargarResumen();
        cargarTabla();
    }

    // ------------------------------------------------------------- acciones

    @FXML
    private void onNuevaCompra() {
        mainController.irA("compra-form-view");
    }

    @FXML
    private void onKardex() {
        verKardex(tabla.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onReponer() {
        reponer(tabla.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onReponerDesdeKardex() {
        notificador.cerrarPanel();
        reponer(enKardex);
    }

    @FXML
    private void onCerrarKardex() {
        notificador.cerrarPanel();
    }

    @FXML
    private void onExportarExcel() {
        exportador.guardar(tabla.getScene().getWindow(), "Exportar inventario",
                "inventario_" + LocalDate.now(), Exportador.Formato.EXCEL, excelService::generarInventario);
    }

    // ----------------------------------------------------------------- apoyo

    /** Abre la compra con el producto ya agregado; sugiere llegar al doble del stock minimo. */
    private void reponer(Producto p) {
        if (p == null) {
            return;
        }
        mainController.irA("compra-form-view");
        int sugerida = Math.max(p.getStockMinimo(), p.getStockMinimo() * 2 - Math.max(0, p.getStock().intValue()));
        compraForm.agregarDesdeInventario(p, sugerida);
    }

    private void verKardex(Producto p) {
        if (p == null) {
            return;
        }
        enKardex = p;
        List<MovimientoInventario> movimientos = service.kardex(p.getId());
        String entradas = InventarioService.texto(movimientos.stream().map(MovimientoInventario::getEntrada)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        String salidas = InventarioService.texto(movimientos.stream().map(MovimientoInventario::getSalida)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));

        lblKarProducto.setText(p.getNombre());
        lblKarSubtitulo.setText(p.getCodigoProducto() + " · Stock minimo " + p.getStockMinimo()
                + " · " + movimientos.size() + " movimientos");
        lblKarStock.setText(InventarioService.texto(p.getStock()) + " " + p.getAbreviaturaUnidad());
        lblKarStock.getStyleClass().setAll("label", "resumen-filtro-valor", "stock-" + Tablas.claseStock(p));
        lblKarCosto.setText(p.getCostoPromedio() == null ? "—" : Tablas.MONEDA.format(p.getCostoPromedio()));
        lblKarValor.setText(Tablas.MONEDA.format(InventarioService.valorizado(p)));
        lblKarEntradas.setText(entradas + " unidades ingresadas");
        lblKarSalidas.setText(salidas + " unidades salieron");

        // Lo mas reciente arriba.
        List<MovimientoInventario> recientes = new ArrayList<>(movimientos);
        Collections.reverse(recientes);
        tablaKardex.getItems().setAll(recientes);
        notificador.abrirPanel(panelKardex);
    }

    private void cargarResumen() {
        ResumenInventario r = service.resumen();
        lblResUnidades.setText(InventarioService.texto(r.unidades()));
        lblResProductos.setText(r.productos() + " productos en catalogo");
        lblResCosto.setText(Tablas.MONEDA.format(r.valorCosto()));
        lblResVenta.setText(Tablas.MONEDA.format(r.valorVenta()));
        BigDecimal ventaSinIgv = r.valorVenta().divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        lblResMargen.setText(r.valorCosto().signum() == 0 ? "Sin costo registrado"
                : "Margen potencial " + Tablas.MONEDA.format(ventaSinIgv.subtract(r.valorCosto())) + " sin IGV");
        lblResBajo.setText(String.valueOf(r.stockBajo()));
        lblResSin.setText(String.valueOf(r.sinStock()));
    }

    private void cargarTabla() {
        Page<Producto> pagina = service.listar(txtBuscar.getText(), cmbFiltro.getValue(),
                paginador.getPagina(), paginador.getTamanio());
        datos.setAll(pagina.getContent());
        paginador.actualizar(pagina);
        Animations.fadeIn(tabla, Duration.millis(260)).play();
    }
}
