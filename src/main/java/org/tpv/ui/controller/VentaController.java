package org.tpv.ui.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.tpv.config.Configuracion;
import org.tpv.domain.Cliente;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;
import org.tpv.domain.Producto;
import org.tpv.repository.ConfiguracionRepository;
import org.tpv.service.ClienteService;
import org.tpv.service.ImpresoraService;
import org.tpv.service.ProductoService;
import org.tpv.service.VentaService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class VentaController {

    // ===== COMPONENTES FXML =====
    @FXML private TextField txtCodigoBarra;
    @FXML private Button btnAñadir;
    @FXML private Button btnIncrementar;
    @FXML private Button btnDecrementar;
    @FXML private Button btnEliminar;
    @FXML private Button btnNuevaVenta;
    @FXML private Button btnCobrar;
    @FXML private Label lblCantidadArticulos;
    @FXML private Label lblUsuario;
    @FXML private Label lblFechaHora;

    @FXML private TableView<LineaFactura> tablaTicket;
    @FXML private TableColumn<LineaFactura, String>     colCodigo;
    @FXML private TableColumn<LineaFactura, String>     colNombre;
    @FXML private TableColumn<LineaFactura, Integer>    colCantidad;
    @FXML private TableColumn<LineaFactura, BigDecimal> colPrecio;
    @FXML private TableColumn<LineaFactura, BigDecimal> colSubtotal;
    @FXML private TableColumn<LineaFactura, Integer>    colDescuento;

    @FXML private Label lblTotalSinIva;
    @FXML private Label lblIva;
    @FXML private Label lblTotal;
    @FXML private Label lblClienteActual;
    @FXML private Button btnSeleccionarCliente;

    // Método de pago
    @FXML private ToggleButton btnPagoEfectivo;
    @FXML private ToggleButton btnPagoTarjeta;
    @FXML private VBox panelEfectivo;
    @FXML private TextField txtEntregado;
    @FXML private Label lblCambio;

    // ===== SERVICIOS Y DATOS =====
    private ProductoService productoService;
    private VentaService ventaService;
    private Configuracion config;
    private ImpresoraService impresoraService;
    private ConfiguracionRepository configRepository;
    private ClienteService clienteService;

    private final ToggleGroup togglePago = new ToggleGroup();
    private ObservableList<LineaFactura> lineasObservables = FXCollections.observableArrayList();

    // ===== INICIALIZACIÓN =====
    @FXML
    public void initialize() {
        System.out.println("✓ Inicializando VentaController...");
        try {
            productoService  = new ProductoService();
            configRepository = new ConfiguracionRepository();
            clienteService   = new ClienteService();
            config = configRepository.obtenerConfiguracion();

            ventaService = new VentaService(config);
            ventaService.iniciarVenta();
            impresoraService = new ImpresoraService(config);

            configurarTabla();
            configurarSelectorPago();

            txtCodigoBarra.requestFocus();
            iniciarReloj();

            if (lblUsuario     != null) lblUsuario.setText("Usuario: Admin");
            if (lblClienteActual != null) actualizarLabelCliente();

            configurarAtajosTeclado();
            System.out.println("✓ VentaController inicializado correctamente");

        } catch (Exception e) {
            System.err.println("❌ Error al inicializar VentaController: " + e.getMessage());
            e.printStackTrace();
            mostrarError("Error de inicialización", "No se pudo cargar la configuración: " + e.getMessage());
        }
    }

    // ===== CONFIGURAR SELECTOR DE PAGO =====
    private void configurarSelectorPago() {
        btnPagoEfectivo.setToggleGroup(togglePago);
        btnPagoTarjeta.setToggleGroup(togglePago);

        // Efectivo seleccionado por defecto
        btnPagoEfectivo.setSelected(true);
        aplicarEstiloToggle(btnPagoEfectivo, true);
        aplicarEstiloToggle(btnPagoTarjeta, false);
        panelEfectivo.setVisible(true);
        panelEfectivo.setManaged(true);

        // Evitar deselección total (siempre uno activo)
        togglePago.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) oldVal.setSelected(true);
        });
    }

    /** Aplica estilo visual al botón según si está seleccionado o no */
    private void aplicarEstiloToggle(ToggleButton btn, boolean seleccionado) {
        if (seleccionado) {
            btn.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;"
                    + (btn == btnPagoEfectivo
                    ? "-fx-background-radius: 6 0 0 6;"
                    : "-fx-background-radius: 0 6 6 0;")
                    + "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-cursor: hand;");
        } else {
            btn.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;"
                    + (btn == btnPagoEfectivo
                    ? "-fx-background-radius: 6 0 0 6;"
                    : "-fx-background-radius: 0 6 6 0;")
                    + "-fx-background-color: #dee2e6; -fx-text-fill: #495057; -fx-cursor: hand;");
        }
    }

    /** Llamado desde FXML cuando se pulsa cualquiera de los toggle buttons */
    @FXML
    private void onMetodoPagoChanged() {
        boolean esEfectivo = btnPagoEfectivo.isSelected();

        aplicarEstiloToggle(btnPagoEfectivo, esEfectivo);
        aplicarEstiloToggle(btnPagoTarjeta, !esEfectivo);

        panelEfectivo.setVisible(esEfectivo);
        panelEfectivo.setManaged(esEfectivo);

        if (!esEfectivo) {
            txtEntregado.clear();
            lblCambio.setText("—");
        } else {
            recalcularCambio();
        }

        ventaService.setMetodoPago(esEfectivo ? "EFECTIVO" : "TARJETA");
    }

    /** Llamado desde FXML al teclear en el campo de importe entregado */
    @FXML
    private void onEntregadoChanged() {
        recalcularCambio();
    }

    private void recalcularCambio() {
        String texto = txtEntregado.getText().trim().replace(",", ".");
        if (texto.isEmpty()) { lblCambio.setText("—"); return; }
        try {
            BigDecimal entregado = new BigDecimal(texto);
            BigDecimal total     = ventaService.finalizarVenta().getTotalConIva();
            BigDecimal cambio    = entregado.subtract(total).setScale(2, RoundingMode.HALF_UP);
            if (cambio.compareTo(BigDecimal.ZERO) >= 0) {
                lblCambio.setText(String.format("%.2f", cambio));
                lblCambio.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
            } else {
                lblCambio.setText(String.format("%.2f", cambio));
                lblCambio.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            }
        } catch (NumberFormatException e) {
            lblCambio.setText("—");
        }
    }

    private String getMetodoPagoSeleccionado() {
        return btnPagoTarjeta.isSelected() ? "TARJETA" : "EFECTIVO";
    }

    /** Devuelve el importe entregado por el cliente (sólo aplica a efectivo) */
    private BigDecimal getEntregadoCliente() {
        try {
            return new BigDecimal(txtEntregado.getText().trim().replace(",", "."));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ===== RELOJ =====
    private void iniciarReloj() {
        Thread reloj = new Thread(() -> {
            while (true) {
                try {
                    javafx.application.Platform.runLater(() -> {
                        if (lblFechaHora != null) {
                            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                            lblFechaHora.setText(java.time.LocalDateTime.now().format(f));
                        }
                    });
                    Thread.sleep(1000);
                } catch (InterruptedException e) { break; }
            }
        });
        reloj.setDaemon(true);
        reloj.start();
    }

    // ===== TABLA =====
    private void configurarTabla() {
        tablaTicket.setEditable(true);

        colCodigo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCodigoProducto()));
        colCodigo.setCellFactory(TextFieldTableCell.forTableColumn());
        colCodigo.setOnEditCommit(e -> {
            if (e.getNewValue() == null || e.getNewValue().trim().isEmpty()) { tablaTicket.refresh(); return; }
            e.getRowValue().setCodigoProducto(e.getNewValue().trim());
        });

        colNombre.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombreProducto()));
        colNombre.setCellFactory(TextFieldTableCell.forTableColumn());
        colNombre.setOnEditCommit(e -> {
            if (e.getNewValue() == null || e.getNewValue().trim().isEmpty()) { tablaTicket.refresh(); return; }
            e.getRowValue().setNombreProducto(e.getNewValue().trim());
        });

        colCantidad.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getCantidad()).asObject());
        colCantidad.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        colCantidad.setOnEditCommit(e -> {
            if (e.getNewValue() == null || e.getNewValue() <= 0) { tablaTicket.refresh(); return; }
            e.getRowValue().setCantidad(e.getNewValue());
            ventaService.finalizarVenta().recalcularTotales();
            actualizarVista();
        });

        colPrecio.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getPrecioUnitario()));
        colPrecio.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colPrecio.setOnEditCommit(e -> {
            if (e.getNewValue() == null || e.getNewValue().compareTo(BigDecimal.ZERO) < 0) { tablaTicket.refresh(); return; }
            e.getRowValue().setPrecioUnitario(e.getNewValue());
            ventaService.finalizarVenta().recalcularTotales();
            actualizarVista();
        });

        colDescuento.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getDescuento()).asObject());
        colDescuento.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        colDescuento.setOnEditCommit(e -> {
            int dto = (e.getNewValue() == null) ? 0 : e.getNewValue();
            if (dto < 0 || dto > 100) { tablaTicket.refresh(); return; }
            e.getRowValue().setDescuento(dto);
            ventaService.finalizarVenta().recalcularTotales();
            actualizarVista();
        });

        colSubtotal.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getTotalConIva()));
        colSubtotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(String.format("%.2f€", v));
                setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #666;");
            }
        });

        tablaTicket.setItems(lineasObservables);
    }

    // ===== AÑADIR PRODUCTO =====
    @FXML
    private void onAñadirProducto() {
        String codigo = txtCodigoBarra.getText().trim();
        if (codigo.isEmpty()) {
            try {
                Producto p = mostrarDialogoCrearProductoSinCodigo();
                if (p != null) { ventaService.añadirProducto(p); actualizarVista(); }
            } catch (SQLException e) { mostrarError("Error BD", e.getMessage()); }
            txtCodigoBarra.clear(); txtCodigoBarra.requestFocus();
            return;
        }
        try {
            Producto p = productoService.buscarPorCodigo(codigo);
            if (p == null) {
                p = mostrarDialogoCrearProducto(codigo);
                if (p == null) { txtCodigoBarra.clear(); txtCodigoBarra.requestFocus(); return; }
            }
            ventaService.añadirProducto(p);
            actualizarVista();
            txtCodigoBarra.clear(); txtCodigoBarra.requestFocus();
        } catch (SQLException e) { mostrarError("Error BD", e.getMessage()); }
    }

    // ===== INCREMENTAR / DECREMENTAR / ELIMINAR =====
    @FXML
    private void onIncrementar() {
        LineaFactura s = tablaTicket.getSelectionModel().getSelectedItem();
        if (s == null) { mostrarAlerta("Sin selección", "Selecciona una línea"); return; }
        s.setCantidad(s.getCantidad() + 1);
        ventaService.finalizarVenta().recalcularTotales();
        actualizarVista();
    }

    @FXML
    private void onDecrementar() {
        LineaFactura s = tablaTicket.getSelectionModel().getSelectedItem();
        if (s == null) { mostrarAlerta("Sin selección", "Selecciona una línea"); return; }
        if (s.getCantidad() == 1) {
            Alert c = new Alert(Alert.AlertType.CONFIRMATION, "¿Eliminar esta línea?");
            c.setTitle("Eliminar línea"); c.setHeaderText("La cantidad es 1");
            if (c.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) onEliminarLinea();
            return;
        }
        s.setCantidad(s.getCantidad() - 1);
        ventaService.finalizarVenta().recalcularTotales();
        actualizarVista();
    }

    @FXML
    private void onEliminarLinea() {
        LineaFactura s = tablaTicket.getSelectionModel().getSelectedItem();
        if (s == null) { mostrarAlerta("Sin selección", "Selecciona una línea"); return; }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION);
        c.setTitle("Eliminar línea"); c.setHeaderText("¿Eliminar " + s.getNombreProducto() + "?");
        if (c.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return;
        Factura f = ventaService.finalizarVenta();
        f.getLineas().remove(s); f.recalcularTotales(); actualizarVista();
    }

    // ===== COBRAR =====
    @FXML
    private void onCobrar() {
        if (lineasObservables.isEmpty()) { mostrarAlerta("Venta vacía", "Añade productos antes de cobrar"); return; }

        String metodoPago = getMetodoPagoSeleccionado();

        // Validación efectivo: importe entregado >= total
        if (metodoPago.equals("EFECTIVO")) {
            BigDecimal entregado = getEntregadoCliente();
            BigDecimal total     = ventaService.finalizarVenta().getTotalConIva();
            /*
            if (entregado.compareTo(BigDecimal.ZERO) <= 0) {
                mostrarAlerta("Importe no introducido", "Introduce el importe que entrega el cliente");
                txtEntregado.requestFocus();
                return;
            }

            if (entregado.compareTo(total) < 0) {
                mostrarAlerta("Importe insuficiente",
                        String.format("El cliente entrega %.2f€ pero el total es %.2f€", entregado, total));
                txtEntregado.requestFocus();
                return;
            }

             */
        }

        // Registrar método de pago e importe entregado en la factura
        ventaService.setMetodoPago(metodoPago);
        if (metodoPago.equals("EFECTIVO")) {
            ventaService.setEntregadoCliente(getEntregadoCliente());
        }

        // Guardar en BD
        Factura factura = ventaService.finalizarVenta();
        try {
            ventaService.guardarVenta();
            System.out.println("✓ Factura guardada: " + factura.getNumeroFactura());
        } catch (SQLException e) {
            mostrarError("Error al guardar", e.getMessage());
            return;
        }

        //actualizarProductosEnBD(factura);
        actualizarClienteEnBD();

        // Diálogo de confirmación
        String clienteInfo  = factura.getClienteNombre() != null ? "\nCliente: " + factura.getClienteNombre() : "";
        String pagoInfo     = metodoPago.equals("TARJETA") ? "💳 Tarjeta" : "💵 Efectivo";
        String cambioInfo   = "";
        if (metodoPago.equals("EFECTIVO")) {
            BigDecimal entregado = factura.getEntregadoCliente();
            BigDecimal cambio    = entregado.subtract(factura.getTotalConIva()).setScale(2, RoundingMode.HALF_UP);
            cambioInfo = String.format("\nEntrega: %.2f€   Cambio: %.2f€", entregado, cambio);
        }

        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Cobro realizado");
        conf.setHeaderText("✓ Venta finalizada correctamente");
        conf.setContentText(String.format(
                "═══════════════════════════════\nFactura: %s%s\n═══════════════════════════════\nBase imponible: %.2f€\nIVA (%d%%):      %.2f€\n───────────────────────────────\nTOTAL:          %.2f€\n═══════════════════════════════\nPago: %s%s\n\n¿Deseas imprimir el ticket?",
                factura.getNumeroFactura(), clienteInfo,
                factura.getTotalSinIva(), config.getIvaGeneral(), factura.getTotalIva(),
                factura.getTotalConIva(), pagoInfo, cambioInfo
        ));

        ButtonType btnImprimir    = new ButtonType("Imprimir");
        ButtonType btnNoImprimir  = new ButtonType("No imprimir");
        ButtonType btnCancelarFin = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        conf.getButtonTypes().setAll(btnImprimir, btnNoImprimir, btnCancelarFin);

        Optional<ButtonType> res = conf.showAndWait();
        if (res.isPresent()) {
            if (res.get() == btnImprimir)    imprimirTicket(factura);
            else if (res.get() == btnCancelarFin) return;
        }

        System.out.println("✓ Venta cobrada: " + factura.getTotalConIva() + "€ (" + metodoPago + ")");
        onNuevaVenta();
    }

    // ===== NUEVA VENTA =====
    @FXML
    private void onNuevaVenta() {
        if (!lineasObservables.isEmpty()) {
            Alert c = new Alert(Alert.AlertType.CONFIRMATION, "Se perderá la venta actual si no ha sido cobrada");
            c.setTitle("Nueva venta"); c.setHeaderText("¿Iniciar nueva venta?");
            if (c.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return;
        }
        ventaService.iniciarVenta();
        lineasObservables.clear();
        actualizarTotales(new Factura());
        txtCodigoBarra.clear();
        txtEntregado.clear();
        lblCambio.setText("—");
        // Restablecer selector a efectivo
        btnPagoEfectivo.setSelected(true);
        aplicarEstiloToggle(btnPagoEfectivo, true);
        aplicarEstiloToggle(btnPagoTarjeta, false);
        panelEfectivo.setVisible(true);
        panelEfectivo.setManaged(true);
        txtCodigoBarra.requestFocus();
    }

    // ===== DIÁLOGOS PRODUCTO =====
    private Producto mostrarDialogoCrearProducto(String codigo) throws SQLException {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.setTitle("Nuevo Producto");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setMinWidth(480);

        ButtonType btnCrear   = new ButtonType("✔  Crear producto", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar",          ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCrear, btnCancelar);

        // ── Estilos reutilizables ──────────────────────────────────────────────
        String estiloLabel = "-fx-font-size: 11px; -fx-font-weight: bold; "
                + "-fx-text-fill: #6c757d; -fx-padding: 0 0 3 2;";
        String estiloInput = "-fx-font-size: 15px; -fx-padding: 10 12; "
                + "-fx-background-color: white; -fx-border-color: #ced4da; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-pref-height: 42px;";
        String estiloInputFoco = estiloInput + "-fx-border-color: #3498db; "
                + "-fx-effect: dropshadow(gaussian, rgba(52,152,219,0.25), 6, 0, 0, 0);";

        // ── Cabecera personalizada ─────────────────────────────────────────────
        VBox cabecera = new VBox(4);
        cabecera.setStyle("-fx-background-color: #2c3e50; -fx-padding: 20 24 18 24;");
        Label lblTitulo   = new Label("Nuevo Producto");
        lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label lblSubtitulo = new Label("Código: " + codigo);
        lblSubtitulo.setStyle("-fx-font-size: 12px; -fx-text-fill: #95a5a6;");
        cabecera.getChildren().addAll(lblTitulo, lblSubtitulo);

        // ── Campos ────────────────────────────────────────────────────────────
        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Ej: Café con leche, Barra de pan...");
        txtNombre.setStyle(estiloInput);
        txtNombre.setMaxWidth(Double.MAX_VALUE);
        txtNombre.focusedProperty().addListener((o, ov, nv) ->
                txtNombre.setStyle(nv ? estiloInputFoco : estiloInput));

        TextField txtPrecio = new TextField();
        txtPrecio.setPromptText("0.00");
        txtPrecio.setStyle(estiloInput);
        txtPrecio.setMaxWidth(Double.MAX_VALUE);
        txtPrecio.focusedProperty().addListener((o, ov, nv) ->
                txtPrecio.setStyle(nv ? estiloInputFoco : estiloInput));

        // ── Layout ────────────────────────────────────────────────────────────
        VBox cuerpo = new VBox(16);
        cuerpo.setStyle("-fx-padding: 24 24 8 24; -fx-background-color: #f8f9fa;");

        VBox grupoNombre = new VBox(4,
                new Label("NOMBRE DEL PRODUCTO") {{ setStyle(estiloLabel); }},
                txtNombre
        );

        HBox filaPrecio = new HBox(0);
        Label euroSufijo = new Label("€");
        euroSufijo.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #495057; "
                + "-fx-padding: 10 12; -fx-background-color: #e9ecef; "
                + "-fx-border-color: #ced4da; -fx-border-radius: 0 6 6 0; "
                + "-fx-background-radius: 0 6 6 0; -fx-pref-height: 42px;");
        txtPrecio.setStyle(estiloInput + "-fx-border-radius: 6 0 0 6; -fx-background-radius: 6 0 0 6;");
        HBox.setHgrow(txtPrecio, javafx.scene.layout.Priority.ALWAYS);
        filaPrecio.getChildren().addAll(txtPrecio, euroSufijo);

        VBox grupoPrecio = new VBox(4,
                new Label("PRECIO CON IVA INCLUIDO") {{ setStyle(estiloLabel); }},
                filaPrecio
        );

        Label lblAviso = new Label("ℹ El precio introducido debe incluir el IVA");
        lblAviso.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        cuerpo.getChildren().addAll(grupoNombre, grupoPrecio, lblAviso);

        VBox contenedor = new VBox(cabecera, cuerpo);
        dialog.getDialogPane().setContent(contenedor);
        dialog.getDialogPane().setStyle("-fx-padding: 0; -fx-background-color: #f8f9fa;");

        // ── Botón Crear desactivado hasta validar ─────────────────────────────
        Node btnCrearNode = dialog.getDialogPane().lookupButton(btnCrear);
        btnCrearNode.setDisable(true);
        btnCrearNode.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 20; "
                + "-fx-background-radius: 5; -fx-cursor: hand;");

        ChangeListener<String> validar = (o, ov, nv) -> {
            try {
                btnCrearNode.setDisable(
                        txtNombre.getText().trim().isEmpty()
                                || new BigDecimal(txtPrecio.getText().trim().replace(",", "."))
                                .compareTo(BigDecimal.ZERO) <= 0
                );
            } catch (Exception ex) { btnCrearNode.setDisable(true); }
        };
        txtNombre.textProperty().addListener(validar);
        txtPrecio.textProperty().addListener(validar);
        javafx.application.Platform.runLater(txtNombre::requestFocus);

        dialog.setResultConverter(b -> {
            if (b != btnCrear) return null;
            try {
                return productoService.crearProducto(
                        codigo,
                        txtNombre.getText().trim(),
                        new BigDecimal(txtPrecio.getText().trim().replace(",", "."))
                );
            } catch (SQLException e) { mostrarError("Error", e.getMessage()); return null; }
        });
        return dialog.showAndWait().orElse(null);
    }


    private Producto mostrarDialogoCrearProductoSinCodigo() throws SQLException {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.setTitle("Nuevo Producto");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setMinWidth(480);

        ButtonType btnCrear    = new ButtonType("✔  Crear producto", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar",           ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCrear, btnCancelar);

        String estiloLabel = "-fx-font-size: 11px; -fx-font-weight: bold; "
                + "-fx-text-fill: #6c757d; -fx-padding: 0 0 3 2;";
        String estiloInput = "-fx-font-size: 15px; -fx-padding: 10 12; "
                + "-fx-background-color: white; -fx-border-color: #ced4da; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-pref-height: 42px;";
        String estiloInputFoco = estiloInput + "-fx-border-color: #3498db; "
                + "-fx-effect: dropshadow(gaussian, rgba(52,152,219,0.25), 6, 0, 0, 0);";
        String estiloInputOpc  = "-fx-font-size: 15px; -fx-padding: 10 12; "
                + "-fx-background-color: white; -fx-border-color: #e9ecef; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-pref-height: 42px;";

        // ── Cabecera ──────────────────────────────────────────────────────────
        VBox cabecera = new VBox(4);
        cabecera.setStyle("-fx-background-color: #2c3e50; -fx-padding: 20 24 18 24;");
        Label lblTitulo    = new Label("Nuevo Producto");
        lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label lblSubtitulo = new Label("Rellena los datos del producto a añadir");
        lblSubtitulo.setStyle("-fx-font-size: 12px; -fx-text-fill: #95a5a6;");
        cabecera.getChildren().addAll(lblTitulo, lblSubtitulo);

        // ── Campos ────────────────────────────────────────────────────────────
        TextField txtCodigo = new TextField();
        txtCodigo.setPromptText("Se generará automáticamente si se deja vacío");
        txtCodigo.setStyle(estiloInputOpc);
        txtCodigo.setMaxWidth(Double.MAX_VALUE);
        txtCodigo.focusedProperty().addListener((o, ov, nv) ->
                txtCodigo.setStyle(nv ? estiloInputFoco : estiloInputOpc));

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Ej: Café con leche, Barra de pan...");
        txtNombre.setStyle(estiloInput);
        txtNombre.setMaxWidth(Double.MAX_VALUE);
        txtNombre.focusedProperty().addListener((o, ov, nv) ->
                txtNombre.setStyle(nv ? estiloInputFoco : estiloInput));

        TextField txtPrecio = new TextField();
        txtPrecio.setPromptText("0.00");
        txtPrecio.setStyle(estiloInput);
        txtPrecio.setMaxWidth(Double.MAX_VALUE);
        txtPrecio.focusedProperty().addListener((o, ov, nv) ->
                txtPrecio.setStyle(nv ? estiloInputFoco : estiloInput));

        // ── Layout ────────────────────────────────────────────────────────────
        VBox cuerpo = new VBox(16);
        cuerpo.setStyle("-fx-padding: 24 24 8 24; -fx-background-color: #f8f9fa;");

        Label lblOpcional = new Label("OPCIONAL");
        lblOpcional.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: white; "
                + "-fx-background-color: #95a5a6; -fx-background-radius: 3; -fx-padding: 1 5;");
        HBox headerCodigo = new HBox(8,
                new Label("CÓDIGO DE BARRAS") {{ setStyle(estiloLabel); }},
                lblOpcional
        );
        headerCodigo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox grupoCodigo = new VBox(4, headerCodigo, txtCodigo);

        Label asterisco1 = new Label("*");
        asterisco1.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 13px;");
        HBox headerNombre = new HBox(3,
                new Label("NOMBRE DEL PRODUCTO") {{ setStyle(estiloLabel); }},
                asterisco1
        );
        headerNombre.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox grupoNombre = new VBox(4, headerNombre, txtNombre);

        HBox filaPrecio = new HBox(0);
        Label euroSufijo = new Label("€");
        euroSufijo.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #495057; "
                + "-fx-padding: 10 12; -fx-background-color: #e9ecef; "
                + "-fx-border-color: #ced4da; -fx-border-radius: 0 6 6 0; "
                + "-fx-background-radius: 0 6 6 0; -fx-pref-height: 42px;");
        txtPrecio.setStyle(estiloInput + "-fx-border-radius: 6 0 0 6; -fx-background-radius: 6 0 0 6;");
        HBox.setHgrow(txtPrecio, javafx.scene.layout.Priority.ALWAYS);
        filaPrecio.getChildren().addAll(txtPrecio, euroSufijo);

        Label asterisco2 = new Label("*");
        asterisco2.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 13px;");
        HBox headerPrecio = new HBox(3,
                new Label("PRECIO CON IVA INCLUIDO") {{ setStyle(estiloLabel); }},
                asterisco2
        );
        headerPrecio.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox grupoPrecio = new VBox(4, headerPrecio, filaPrecio);

        Separator sep = new Separator();
        sep.setStyle("-fx-padding: 4 0;");

        Label lblAviso = new Label("* Campos obligatorios  ·  ℹ El precio debe incluir el IVA");
        lblAviso.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        cuerpo.getChildren().addAll(grupoCodigo, grupoNombre, grupoPrecio, sep, lblAviso);

        VBox contenedor = new VBox(cabecera, cuerpo);
        dialog.getDialogPane().setContent(contenedor);
        dialog.getDialogPane().setStyle("-fx-padding: 0; -fx-background-color: #f8f9fa;");

        // ── Validación ────────────────────────────────────────────────────────
        Node btnCrearNode = dialog.getDialogPane().lookupButton(btnCrear);
        btnCrearNode.setDisable(true);
        btnCrearNode.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 20; "
                + "-fx-background-radius: 5; -fx-cursor: hand;");

        ChangeListener<String> validar = (o, ov, nv) -> {
            try {
                btnCrearNode.setDisable(
                        txtNombre.getText().trim().isEmpty()
                                || new BigDecimal(txtPrecio.getText().trim().replace(",", "."))
                                .compareTo(BigDecimal.ZERO) <= 0
                );
            } catch (Exception ex) { btnCrearNode.setDisable(true); }
        };
        txtNombre.textProperty().addListener(validar);
        txtPrecio.textProperty().addListener(validar);
        javafx.application.Platform.runLater(txtNombre::requestFocus);

        dialog.setResultConverter(b -> {
            if (b != btnCrear) return null;
            try {
                String cod = txtCodigo.getText().trim();
                if (cod.isEmpty()) cod = "PROD-" + System.currentTimeMillis();
                return productoService.crearProducto(
                        cod,
                        txtNombre.getText().trim(),
                        new BigDecimal(txtPrecio.getText().trim().replace(",", "."))
                );
            } catch (SQLException e) { mostrarError("Error", e.getMessage()); return null; }
        });
        return dialog.showAndWait().orElse(null);
    }

    // ===== CLIENTE =====
    @FXML
    private void onSeleccionarCliente() {
        try {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Cliente"); a.setHeaderText("¿Qué deseas hacer?");
            ButtonType bBuscar  = new ButtonType("Buscar");
            ButtonType bNuevo   = new ButtonType("Nuevo");
            ButtonType bContado = new ButtonType("Al contado");
            ButtonType bCancel  = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(bBuscar, bNuevo, bContado, bCancel);
            a.showAndWait().ifPresent(r -> {
                try {
                    if (r == bBuscar)       buscarCliente();
                    else if (r == bNuevo)   crearNuevoCliente();
                    else if (r == bContado) { ventaService.asignarCliente(clienteService.obtenerClientePorDefecto()); actualizarLabelCliente(); }
                } catch (SQLException e) { mostrarError("Error", e.getMessage()); }
            });
        } catch (Exception e) { mostrarError("Error", e.getMessage()); }
    }

    private void buscarCliente() throws SQLException {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Buscar Cliente"); d.setHeaderText("DNI o nombre"); d.setContentText("Búsqueda:");
        d.showAndWait().ifPresent(busq -> {
            if (busq.trim().isEmpty()) return;
            try {
                List<Cliente> lista = clienteService.buscarPorNombreODni(busq.trim());
                if (lista.isEmpty()) {
                    Alert i = new Alert(Alert.AlertType.INFORMATION, "¿Crear nuevo cliente?");
                    i.setTitle("No encontrado"); i.setHeaderText("Cliente no encontrado");
                    ButtonType si = new ButtonType("Sí"), no = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
                    i.getButtonTypes().setAll(si, no);
                    if (i.showAndWait().filter(r -> r == si).isPresent()) crearNuevoCliente();
                    return;
                }
                Cliente elegido;
                if (lista.size() > 1) {
                    List<String> ops = lista.stream().map(c -> c.getNombre() + " - " + c.getDni()).toList();
                    ChoiceDialog<String> ch = new ChoiceDialog<>(ops.get(0), ops);
                    ch.setTitle("Seleccionar"); ch.setHeaderText("Múltiples resultados"); ch.setContentText("Elige:");
                    Optional<String> sel = ch.showAndWait();
                    if (sel.isEmpty()) return;
                    elegido = lista.get(ops.indexOf(sel.get()));
                } else elegido = lista.get(0);
                ventaService.asignarCliente(elegido); actualizarLabelCliente();
            } catch (SQLException ex) { mostrarError("Error", ex.getMessage()); }
        });
    }

    private void crearNuevoCliente() throws SQLException {
        Dialog<Cliente> dialog = new Dialog<>();
        dialog.setTitle("Nuevo Cliente"); dialog.setHeaderText("Datos del cliente");
        ButtonType btnG = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnC = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnG, btnC);

        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10);
        g.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));
        TextField tDni = new TextField(); tDni.setPromptText("Opcional");
        TextField tNom = new TextField(); tNom.setPromptText("Nombre completo");
        TextField tTel = new TextField(); tTel.setPromptText("Opcional");
        TextField tDir = new TextField(); tDir.setPromptText("Opcional");
        TextField tEmail = new TextField(); tEmail.setPromptText("Opcional");
        g.add(new Label("DNI:"),       0,0); g.add(tDni,   1,0);
        g.add(new Label("Nombre:*"),   0,1); g.add(tNom,   1,1);
        g.add(new Label("Teléfono:"),  0,2); g.add(tTel,   1,2);
        g.add(new Label("Dirección:"), 0,3); g.add(tDir,   1,3);
        g.add(new Label("Email:"),     0,4); g.add(tEmail, 1,4);
        dialog.getDialogPane().setContent(g);

        Node boton = dialog.getDialogPane().lookupButton(btnG); boton.setDisable(true);
        tNom.textProperty().addListener((o,ov,nv) -> boton.setDisable(nv.trim().isEmpty()));
        javafx.application.Platform.runLater(tNom::requestFocus);

        dialog.setResultConverter(b -> {
            if (b != btnG) return null;
            try {
                String dni = tDni.getText().trim();
                if (!dni.isEmpty() && clienteService.buscarPorDni(dni) != null) {
                    mostrarAlerta("DNI duplicado", "Ya existe un cliente con este DNI"); return null;
                }
                return clienteService.crearCliente(dni, tNom.getText().trim(),
                        tTel.getText().trim(), tDir.getText().trim(), tEmail.getText().trim());
            } catch (SQLException e) { mostrarError("Error", e.getMessage()); return null; }
        });
        dialog.showAndWait().ifPresent(c -> { ventaService.asignarCliente(c); actualizarLabelCliente(); });
    }

    // ===== IMPRESIÓN =====
    private void imprimirTicket(Factura factura) {
        String[] impresoras = impresoraService.obtenerImpresorasDisponibles();
        if (impresoras.length == 0) { mostrarError("Sin impresoras", "No se detectaron impresoras"); return; }
        String imp = impresoras[0];
        if (impresoras.length > 1) {
            ChoiceDialog<String> d = new ChoiceDialog<>(impresoras[0], impresoras);
            d.setTitle("Impresora"); d.setHeaderText("Selecciona impresora"); d.setContentText("Impresora:");
            Optional<String> sel = d.showAndWait();
            if (sel.isEmpty()) return;
            imp = sel.get();
        }
        try {
            impresoraService.imprimirTicket(factura, imp);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("OK"); ok.setHeaderText("Ticket impreso"); ok.setContentText("Impresora: " + imp);
            ok.showAndWait();
        } catch (Exception e) { mostrarError("Error al imprimir", e.getMessage()); }
    }

    // ===== AUXILIARES =====
    private void actualizarProductosEnBD(Factura factura) {
        for (LineaFactura l : factura.getLineas()) {
            try {
                Producto p = productoService.buscarPorCodigo(l.getCodigoProducto());
                if (p != null) {
                    boolean cambios = false;
                    if (!p.getNombre().equals(l.getNombreProducto()))           { p.setNombre(l.getNombreProducto()); cambios = true; }
                    if (p.getPrecioBase().compareTo(l.getPrecioUnitario()) != 0) { p.setPrecioBase(l.getPrecioUnitario()); cambios = true; }
                    if (cambios) productoService.actualizarProducto(p);
                }
            } catch (SQLException e) { System.err.println("⚠ Error producto: " + l.getCodigoProducto()); }
        }
    }

    private void actualizarLabelCliente() {
        if (lblClienteActual == null) return;
        Cliente c = ventaService.getClienteActual();
        if (c.esClientePorDefecto()) {
            lblClienteActual.setText(c.getNombre());
            lblClienteActual.setStyle("-fx-text-fill: #95a5a6;");
        } else {
            lblClienteActual.setText("Cliente: " + c.getNombre());
            lblClienteActual.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        }
    }

    private void actualizarClienteEnBD() {
        try {
            Cliente ca = ventaService.getClienteActual();
            if (!ca.esClientePorDefecto() && ca.getId() != null) {
                Cliente bd = clienteService.buscarPorId(ca.getId());
                if (bd != null) {
                    boolean ch = !java.util.Objects.equals(bd.getNombre(),    ca.getNombre())
                            || !java.util.Objects.equals(bd.getTelefono(),  ca.getTelefono())
                            || !java.util.Objects.equals(bd.getDireccion(), ca.getDireccion())
                            || !java.util.Objects.equals(bd.getEmail(),     ca.getEmail());
                    if (ch) clienteService.actualizarCliente(ca);
                }
            }
        } catch (SQLException e) { System.err.println("⚠ Error cliente BD: " + e.getMessage()); }
    }

    private void actualizarVista() {
        Factura f = ventaService.finalizarVenta();
        lineasObservables.clear();
        lineasObservables.addAll(f.getLineas());
        actualizarTotales(f);
        int total = f.getLineas().stream().mapToInt(LineaFactura::getCantidad).sum();
        if (lblCantidadArticulos != null)
            lblCantidadArticulos.setText(total + " artículo" + (total != 1 ? "s" : ""));
        tablaTicket.refresh();
        // Recalcular cambio al actualizar totales (por si cambió el total)
        recalcularCambio();
    }

    private void actualizarTotales(Factura f) {
        lblTotalSinIva.setText(String.format("%.2f€", f.getTotalSinIva()));
        lblIva.setText(String.format("%.2f€", f.getTotalIva()));
        lblTotal.setText(String.format("%.2f€", f.getTotalConIva()));
    }

    private void mostrarAlerta(String t, String m) {
        Alert a = new Alert(Alert.AlertType.WARNING); a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }
    private void mostrarError(String t, String m) {
        Alert a = new Alert(Alert.AlertType.ERROR); a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }
    // ===== ATAJOS DE TECLADO =====
    private void configurarAtajosTeclado() {
        // Usamos un filtro de eventos en el contenedor principal (txtCodigoBarra es un buen punto de partida o la propia escena)
        txtCodigoBarra.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.F1) {
                        onCobrar();
                        event.consume();
                    } else if (event.getCode() == KeyCode.F2) {
                        onNuevaVenta();
                        event.consume();
                    }
                });
            }
        });
    }
}