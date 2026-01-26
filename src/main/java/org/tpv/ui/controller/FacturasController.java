package org.tpv.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;
import org.tpv.service.FacturaService;
import org.tpv.service.ImpresoraService;
import org.tpv.config.Configuracion;
import org.tpv.repository.ConfiguracionRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FacturasController {

    @FXML private DatePicker dateFechaInicio;
    @FXML private DatePicker dateFechaFin;
    @FXML private TextField txtBusquedaCliente;
    @FXML private Button btnFiltrar;
    @FXML private Button btnBuscarCliente;
    @FXML private Button btnLimpiarFiltros;

    @FXML private TableView<Factura> tablaFacturas;
    @FXML private TableColumn<Factura, String> colNumero;
    @FXML private TableColumn<Factura, String> colFecha;
    @FXML private TableColumn<Factura, String> colCliente;
    @FXML private TableColumn<Factura, BigDecimal> colTotal;
    @FXML private TableColumn<Factura, Void> colAcciones;

    @FXML private Label lblTotalFacturas;
    @FXML private Label lblSumaTotal;

    private FacturaService facturaService;
    private ImpresoraService impresoraService;
    private ObservableList<Factura> facturasObservables = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        System.out.println("✓ Inicializando FacturasController...");

        facturaService = new FacturaService();

        // Inicializar servicio de impresión
        try {
            Configuracion config = new ConfiguracionRepository().findFirst();
            impresoraService = new ImpresoraService(config);
        } catch (Exception e) {
            System.err.println("Error al inicializar ImpresoraService: " + e.getMessage());
        }

        configurarTabla();
        cargarFacturas();

        if (txtBusquedaCliente != null) {
            txtBusquedaCliente.setOnAction(event -> buscarPorCliente());
        }

        System.out.println("✓ FacturasController inicializado");
    }

    private void configurarTabla() {
        colNumero.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNumeroFactura()));

        colFecha.setCellValueFactory(data -> {
            LocalDateTime fecha = data.getValue().getFechaEmision();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return new SimpleStringProperty(fecha.format(formatter));
        });

        colCliente.setCellValueFactory(data -> {
            String cliente = data.getValue().getClienteNombre();
            return new SimpleStringProperty(cliente != null ? cliente : "AL CONTADO");
        });

        colTotal.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue().getTotalConIva()));

        colTotal.setCellFactory(col -> new TableCell<Factura, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal total, boolean empty) {
                super.updateItem(total, empty);
                if (empty || total == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f €", total));
                }
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<Factura, Void>() {
            private final Button btnVer = new Button("👁️ Ver");
            private final HBox acciones = new HBox(5, btnVer);

            {
                btnVer.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand;");
                btnVer.setOnAction(event -> {
                    Factura factura = getTableView().getItems().get(getIndex());
                    verDetalleFactura(factura);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(acciones);
                }
            }
        });

        tablaFacturas.setItems(facturasObservables);
    }

    private void cargarFacturas() {
        try {
            facturasObservables.clear();
            List<Factura> facturas = facturaService.obtenerTodas();
            facturasObservables.addAll(facturas);
            actualizarEstadisticas();
        } catch (SQLException e) {
            mostrarError("Error", "No se pudieron cargar las facturas: " + e.getMessage());
        }
    }

    @FXML
    private void filtrarFacturas() {
        LocalDate fechaInicio = dateFechaInicio.getValue();
        LocalDate fechaFin = dateFechaFin.getValue();

        if (fechaInicio == null && fechaFin == null) {
            mostrarAlerta("Fechas no seleccionadas", "Selecciona al menos una fecha para filtrar");
            return;
        }

        try {
            facturasObservables.clear();
            List<Factura> facturas = facturaService.buscarPorFechas(
                    fechaInicio != null ? fechaInicio.atStartOfDay() : null,
                    fechaFin != null ? fechaFin.atTime(23, 59, 59) : null
            );
            facturasObservables.addAll(facturas);
            actualizarEstadisticas();
        } catch (SQLException e) {
            mostrarError("Error", "Error al filtrar: " + e.getMessage());
        }
    }

    @FXML
    private void buscarPorCliente() {
        if (txtBusquedaCliente == null) return;
        String busqueda = txtBusquedaCliente.getText().trim();
        if (busqueda.isEmpty()) {
            mostrarAlerta("Búsqueda vacía", "Introduce el nombre o DNI del cliente");
            return;
        }
        try {
            facturasObservables.clear();
            List<Factura> facturas = facturaService.buscarPorCliente(busqueda);
            facturasObservables.addAll(facturas);
            actualizarEstadisticas();
        } catch (SQLException e) {
            mostrarError("Error", "Error al buscar: " + e.getMessage());
        }
    }

    @FXML
    private void limpiarFiltros() {
        dateFechaInicio.setValue(null);
        dateFechaFin.setValue(null);
        if (txtBusquedaCliente != null) txtBusquedaCliente.clear();
        cargarFacturas();
    }

    private void verDetalleFactura(Factura factura) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Detalle de Factura");
        dialog.setHeaderText("Factura: " + factura.getNumeroFactura());

        // Botones del diálogo
        ButtonType btnImprimir = new ButtonType("🖨️ Reimprimir Ticket", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnImprimir, btnCerrar);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        int row = 0;
        grid.add(new Label("Fecha:"), 0, row);
        grid.add(new Label(factura.getFechaEmision().format(formatter)), 1, row++);

        grid.add(new Label("Cliente:"), 0, row);
        String cliente = factura.getClienteNombre() != null ? factura.getClienteNombre() : "AL CONTADO";
        grid.add(new Label(cliente), 1, row++);

        grid.add(new Separator(), 0, row++, 2, 1);
        grid.add(new Label("LÍNEAS DE FACTURA:"), 0, row++, 2, 1);

        TableView<LineaFactura> tablaLineas = new TableView<>();
        tablaLineas.setPrefHeight(200);

        TableColumn<LineaFactura, String> colProd = new TableColumn<>("PRODUCTO");
        colProd.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNombreProducto()));
        colProd.setPrefWidth(200);

        TableColumn<LineaFactura, Integer> colCant = new TableColumn<>("CANT.");
        colCant.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getCantidad()));
        colCant.setPrefWidth(60);

        TableColumn<LineaFactura, BigDecimal> colPrec = new TableColumn<>("PRECIO");
        colPrec.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getPrecioUnitario()));
        colPrec.setPrefWidth(80);

        TableColumn<LineaFactura, BigDecimal> colTot = new TableColumn<>("TOTAL");
        colTot.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getTotalConIva()));
        colTot.setPrefWidth(80);

        colPrec.setCellFactory(col -> new TableCell<LineaFactura, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal precio, boolean empty) {
                super.updateItem(precio, empty);
                setText(empty || precio == null ? null : String.format("%.2f €", precio));
            }
        });

        colTot.setCellFactory(col -> new TableCell<LineaFactura, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal total, boolean empty) {
                super.updateItem(total, empty);
                setText(empty || total == null ? null : String.format("%.2f €", total));
            }
        });

        tablaLineas.getColumns().addAll(colProd, colCant, colPrec, colTot);
        tablaLineas.getItems().addAll(factura.getLineas());

        grid.add(tablaLineas, 0, row++, 2, 1);
        grid.add(new Separator(), 0, row++, 2, 1);

        grid.add(new Label("Base imponible:"), 0, row);
        grid.add(new Label(String.format("%.2f €", factura.getTotalSinIva())), 1, row++);

        grid.add(new Label("IVA:"), 0, row);
        grid.add(new Label(String.format("%.2f €", factura.getTotalIva())), 1, row++);

        Label lblTotal = new Label(String.format("%.2f €", factura.getTotalConIva()));
        lblTotal.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #27ae60;");
        grid.add(new Label("TOTAL:"), 0, row);
        grid.add(lblTotal, 1, row++);

        dialog.getDialogPane().setContent(grid);

        // Manejar acción de reimpresión
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnImprimir) {
                reimprimirTicket(factura);
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void reimprimirTicket(Factura factura) {
        try {
            if (impresoraService != null) {
                impresoraService.imprimirTicket(factura, null); // null usa la impresora por defecto
            } else {
                mostrarError("Error de Impresión", "El servicio de impresión no está disponible.");
            }
        } catch (Exception e) {
            mostrarError("Error de Impresión", "No se pudo reimprimir el ticket: " + e.getMessage());
        }
    }

    private void actualizarEstadisticas() {
        lblTotalFacturas.setText(facturasObservables.size() + " facturas");
        BigDecimal sumaTotal = facturasObservables.stream()
                .map(Factura::getTotalConIva)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblSumaTotal.setText(String.format("Total: %.2f €", sumaTotal));
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}
