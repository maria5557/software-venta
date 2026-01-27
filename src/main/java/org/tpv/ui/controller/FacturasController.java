package org.tpv.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;
import org.tpv.service.ClienteService;
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
    private ClienteService clienteService;

    @FXML
    public void initialize() {
        facturaService = new FacturaService();
        try {
            Configuracion config = new ConfiguracionRepository().findFirst();
            impresoraService = new ImpresoraService(config);
            clienteService = new ClienteService();

        } catch (Exception e) {
            System.err.println("Error al inicializar ImpresoraService: " + e.getMessage());
        }

        configurarTabla();
        cargarFacturas();

        if (txtBusquedaCliente != null) {
            txtBusquedaCliente.setOnAction(event -> buscarPorCliente());
        }
    }

    private void configurarTabla() {
        colNumero.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNumeroFactura()));
        colFecha.setCellValueFactory(data -> {
            LocalDateTime fecha = data.getValue().getFechaEmision();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return new SimpleStringProperty(fecha.format(formatter));
        });
        colCliente.setCellValueFactory(data -> {
            String cliente = data.getValue().getClienteNombre();
            return new SimpleStringProperty(cliente != null ? cliente : "AL CONTADO");
        });
        colTotal.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getTotalConIva()));
        colTotal.setCellFactory(col -> new TableCell<Factura, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal total, boolean empty) {
                super.updateItem(total, empty);
                setText(empty || total == null ? null : String.format("%.2f €", total));
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<Factura, Void>() {
            private final Button btnVer = new Button("Ver");
            {
                btnVer.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand;");
                btnVer.setOnAction(event -> verDetalleFactura(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnVer);
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
        dialog.setTitle("Detalle Exhaustivo de Factura");
        dialog.setHeaderText("Factura: " + factura.getNumeroFactura());

        ButtonType btnImprimir = new ButtonType("🖨️ Reimprimir Ticket", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnImprimir, btnCerrar);
        dialog.getDialogPane().setPrefWidth(850); // Más ancho para ver todo bien

        VBox mainLayout = new VBox(15);
        mainLayout.setPadding(new javafx.geometry.Insets(20));

        // --- INFO CABECERA ---
        GridPane headerGrid = new GridPane();
        headerGrid.setHgap(20); headerGrid.setVgap(10);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        headerGrid.add(new Label("Fecha:"), 0, 0);
        headerGrid.add(new Label(factura.getFechaEmision().format(formatter)), 1, 0);
        headerGrid.add(new Label("Cliente:"), 0, 1);
        headerGrid.add(new Label(factura.getClienteNombre() != null ? factura.getClienteNombre() : "AL CONTADO"), 1, 1);
        headerGrid.add(new Label("Atendido por:"), 2, 0);
        headerGrid.add(new Label(factura.getEmpleadoNombre() != null ? factura.getEmpleadoNombre() : "Admin"), 3, 0);

        mainLayout.getChildren().add(headerGrid);
        mainLayout.getChildren().add(new Separator());

        // --- TABLA DETALLADA DE LÍNEAS ---
        TableView<LineaFactura> tablaLineas = new TableView<>();
        tablaLineas.setPrefHeight(300);

        TableColumn<LineaFactura, String> colCod = new TableColumn<>("CÓDIGO");
        colCod.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCodigoProducto()));
        colCod.setPrefWidth(120);

        TableColumn<LineaFactura, String> colNom = new TableColumn<>("ARTÍCULO (NOMBRE COMPLETO)");
        colNom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombreProducto()));
        colNom.setPrefWidth(250);

        TableColumn<LineaFactura, Integer> colCan = new TableColumn<>("CANT.");
        colCan.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getCantidad()));
        colCan.setPrefWidth(60);

        TableColumn<LineaFactura, BigDecimal> colPre = new TableColumn<>("PVP UNIT.");
        colPre.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getPrecioUnitario()));
        colPre.setCellFactory(c -> new MoneyCell());
        colPre.setPrefWidth(90);

        TableColumn<LineaFactura, String> colDto = new TableColumn<>("DTO.");
        colDto.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescuento() > 0 ? d.getValue().getDescuento() + "%" : "-"));
        colDto.setPrefWidth(60);

        TableColumn<LineaFactura, BigDecimal> colTot = new TableColumn<>("TOTAL LÍNEA");
        colTot.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getTotalConIva()));
        colTot.setCellFactory(c -> new MoneyCell());
        colTot.setPrefWidth(100);

        tablaLineas.getColumns().addAll(colCod, colNom, colCan, colPre, colDto, colTot);
        tablaLineas.getItems().addAll(factura.getLineas());
        mainLayout.getChildren().add(tablaLineas);

        // --- TOTALES ---
        HBox footer = new HBox(20);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        VBox labels = new VBox(5);
        labels.getChildren().addAll(new Label("Base imponible:"), new Label("IVA:"), new Label("TOTAL:"));

        VBox values = new VBox(5);
        values.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Label lblT = new Label(String.format("%.2f €", factura.getTotalConIva()));
        lblT.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: #27ae60;");
        values.getChildren().addAll(
                new Label(String.format("%.2f €", factura.getTotalSinIva())),
                new Label(String.format("%.2f €", factura.getTotalIva())),
                lblT
        );

        footer.getChildren().addAll(labels, values);
        mainLayout.getChildren().add(footer);

        dialog.getDialogPane().setContent(mainLayout);
        dialog.setResultConverter(b -> {
            if (b == btnImprimir) reimprimirTicket(factura);
            return null;
        });
        dialog.showAndWait();
    }

    private void reimprimirTicket(Factura factura) {
        try {
            if (impresoraService != null) impresoraService.imprimirTicket(factura, null);
            else mostrarError("Error", "Servicio de impresión no disponible.");
        } catch (Exception e) {
            mostrarError("Error", "No se pudo reimprimir: " + e.getMessage());
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

    // Celda personalizada para dinero
    private static class MoneyCell extends TableCell<LineaFactura, BigDecimal> {
        @Override protected void updateItem(BigDecimal item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : String.format("%.2f €", item));
        }
    }
}
