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
import org.tpv.repository.FacturaRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

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

    private FacturaRepository facturaRepository;
    private ObservableList<Factura> facturasObservables = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        System.out.println("✓ Inicializando FacturasController...");

        facturaRepository = new FacturaRepository();

        configurarTabla();
        cargarFacturas();

        // Configurar Enter en el campo de búsqueda
        if (txtBusquedaCliente != null) {
            txtBusquedaCliente.setOnAction(event -> buscarPorCliente());
        }

        System.out.println("✓ FacturasController inicializado");
    }

    private void configurarTabla() {
        // Configurar columnas
        colNumero.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNumeroFactura()));

        colFecha.setCellValueFactory(data -> {
            LocalDateTime fecha = data.getValue().getFecha();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return new SimpleStringProperty(fecha.format(formatter));
        });

        colCliente.setCellValueFactory(data -> {
            String cliente = data.getValue().getClienteNombre();
            return new SimpleStringProperty(cliente != null ? cliente : "AL CONTADO");
        });

        colTotal.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue().getTotalConIva()));

        // Formatear columna de total
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

        // Configurar columna de acciones
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

            List<Factura> facturas = facturaRepository.findAll();
            facturasObservables.addAll(facturas);

            actualizarEstadisticas();

            System.out.println("✓ Facturas cargadas: " + facturasObservables.size());

        } catch (SQLException e) {
            mostrarError("Error", "No se pudieron cargar las facturas: " + e.getMessage());
            e.printStackTrace();
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

            List<Factura> facturas = facturaRepository.findByFechaRango(
                    fechaInicio != null ? fechaInicio.atStartOfDay() : null,
                    fechaFin != null ? fechaFin.atTime(23, 59, 59) : null
            );

            facturasObservables.addAll(facturas);
            actualizarEstadisticas();

            if (facturasObservables.isEmpty()) {
                mostrarAlerta("Sin resultados", "No se encontraron facturas en ese rango de fechas");
            }

        } catch (SQLException e) {
            mostrarError("Error", "Error al filtrar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void buscarPorCliente() {
        if (txtBusquedaCliente == null) {
            return;
        }

        String busqueda = txtBusquedaCliente.getText().trim();

        if (busqueda.isEmpty()) {
            mostrarAlerta("Búsqueda vacía", "Introduce el nombre o DNI del cliente");
            return;
        }

        try {
            facturasObservables.clear();

            List<Factura> facturas = facturaRepository.findByCliente(busqueda);
            facturasObservables.addAll(facturas);

            actualizarEstadisticas();

            if (facturasObservables.isEmpty()) {
                mostrarAlerta("Sin resultados", "No se encontraron facturas para ese cliente");
            }

        } catch (SQLException e) {
            mostrarError("Error", "Error al buscar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void limpiarFiltros() {
        dateFechaInicio.setValue(null);
        dateFechaFin.setValue(null);
        if (txtBusquedaCliente != null) {
            txtBusquedaCliente.clear();
        }
        cargarFacturas();
    }

    private void verDetalleFactura(Factura factura) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Detalle de Factura");
        dialog.setHeaderText("Factura: " + factura.getNumeroFactura());

        ButtonType btnCerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(btnCerrar);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        int row = 0;
        grid.add(new Label("Fecha:"), 0, row);
        grid.add(new Label(factura.getFecha().format(formatter)), 1, row++);

        grid.add(new Label("Cliente:"), 0, row);
        String cliente = factura.getClienteNombre() != null ? factura.getClienteNombre() : "AL CONTADO";
        grid.add(new Label(cliente), 1, row++);

        // Separator
        grid.add(new Separator(), 0, row++, 2, 1);

        grid.add(new Label("LÍNEAS DE FACTURA:"), 0, row++, 2, 1);

        // Tabla de líneas
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

        // Formatear columnas de dinero
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

        // Separator
        grid.add(new Separator(), 0, row++, 2, 1);

        // Totales
        grid.add(new Label("Base imponible:"), 0, row);
        grid.add(new Label(String.format("%.2f €", factura.getTotalSinIva())), 1, row++);

        grid.add(new Label("IVA:"), 0, row);
        grid.add(new Label(String.format("%.2f €", factura.getTotalIva())), 1, row++);

        Label lblTotal = new Label(String.format("%.2f €", factura.getTotalConIva()));
        lblTotal.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #27ae60;");
        grid.add(new Label("TOTAL:"), 0, row);
        grid.add(lblTotal, 1, row++);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
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