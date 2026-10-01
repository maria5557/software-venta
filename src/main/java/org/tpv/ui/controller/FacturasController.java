package org.tpv.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.tpv.domain.Cliente;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;
import org.tpv.domain.RegistroAuditoria;
import org.tpv.repository.FacturaRepository;
import org.tpv.ui.dialog.EditarFacturaDialog;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class FacturasController {

    @FXML private DatePicker dateFechaInicio;
    @FXML private DatePicker dateFechaFin;
    @FXML private TextField txtBusquedaCliente;
    @FXML private Button btnFiltrar;
    @FXML private Button btnBuscarCliente;
    @FXML private Button btnLimpiarFiltros;
    @FXML private CheckBox chkMostrarAnuladas;

    @FXML private TableView<Factura> tablaFacturas;
    @FXML private TableColumn<Factura, String> colNumero;
    @FXML private TableColumn<Factura, String> colFecha;
    @FXML private TableColumn<Factura, String> colCliente;
    @FXML private TableColumn<Factura, String> colEstado;
    @FXML private TableColumn<Factura, BigDecimal> colTotal;
    @FXML private TableColumn<Factura, Void> colAcciones;

    @FXML private Label lblTotalFacturas;
    @FXML private Label lblSumaTotal;

    private FacturaService facturaService;
    private ImpresoraService impresoraService;
    private ObservableList<Factura> facturasObservables = FXCollections.observableArrayList();
    // Vista filtrada: oculta las anuladas salvo que se marque "Mostrar anuladas"
    private FilteredList<Factura> facturasVisibles;
    private static final Comparator<Factura> ORDEN_FECHA_DESC =
            Comparator.comparing(Factura::getFechaEmision).reversed();
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

        chkMostrarAnuladas.selectedProperty().addListener((obs, antes, ahora) -> {
            aplicarFiltroAnuladas();
            actualizarEstadisticas();
        });
    }

    private void aplicarFiltroAnuladas() {
        facturasVisibles.setPredicate(f -> chkMostrarAnuladas.isSelected() || !f.isAnulada());
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
        colEstado.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().isAnulada() ? "ANULADA" : "EMITIDA"));
        colEstado.setCellFactory(col -> new TableCell<Factura, String>() {
            @Override
            protected void updateItem(String estado, boolean empty) {
                super.updateItem(estado, empty);
                if (empty || estado == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(estado);
                    setStyle("ANULADA".equals(estado)
                            ? "-fx-text-fill: #c0392b; -fx-font-weight: bold;"
                            : "-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                }
            }
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
            private final Button btnVer = crearBoton("Ver", "#3498db");
            private final Button btnEditar = crearBoton("✏ Editar", "#f39c12");
            private final Button btnAnular = crearBoton("🚫 Anular", "#e74c3c");
            private final Button btnRestaurar = crearBoton("↩ Restaurar", "#27ae60");
            private final HBox caja = new HBox(6);
            {
                caja.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                btnVer.setOnAction(e -> verDetalleFactura(facturaDeLaFila()));
                btnEditar.setOnAction(e -> editarFactura(facturaDeLaFila()));
                btnAnular.setOnAction(e -> anularFactura(facturaDeLaFila()));
                btnRestaurar.setOnAction(e -> restaurarFactura(facturaDeLaFila()));
            }

            private Factura facturaDeLaFila() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                Factura f = getTableView().getItems().get(getIndex());
                if (f.isAnulada()) {
                    caja.getChildren().setAll(btnVer, btnRestaurar);
                } else {
                    caja.getChildren().setAll(btnVer, btnEditar, btnAnular);
                }
                setGraphic(caja);
            }
        });

        // Las facturas anuladas se distinguen visualmente (fondo rojizo y atenuadas)
        tablaFacturas.setRowFactory(tv -> new TableRow<Factura>() {
            @Override
            protected void updateItem(Factura f, boolean empty) {
                super.updateItem(f, empty);
                if (!empty && f != null && f.isAnulada()) {
                    setStyle("-fx-control-inner-background: #fdecea; -fx-opacity: 0.75;");
                } else {
                    setStyle("");
                }
            }
        });

        facturasVisibles = new FilteredList<>(facturasObservables);
        aplicarFiltroAnuladas();
        tablaFacturas.setItems(facturasVisibles);
    }

    private Button crearBoton(String texto, String colorHex) {
        Button b = new Button(texto);
        b.setStyle("-fx-background-color: " + colorHex + "; -fx-text-fill: white; -fx-cursor: hand; "
                + "-fx-font-size: 12px; -fx-padding: 4 10; -fx-background-radius: 4;");
        return b;
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
        // 1. Intentar obtener los datos completos del cliente antes de mostrar el diálogo
        Cliente clienteCompleto = null;
        if (factura.getClienteId() != null) {
            try {
                // Usamos el servicio para buscar por ID
                clienteCompleto = clienteService.buscarPorId(factura.getClienteId());
            } catch (SQLException e) {
                System.err.println("No se pudieron cargar los datos extra del cliente: " + e.getMessage());
            }
        }

        System.out.println("clienteCompleto: " + factura.getClienteId());



        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Detalle Exhaustivo de Factura");
        dialog.setHeaderText("Factura: " + factura.getNumeroFactura());

        ButtonType btnImprimir = new ButtonType("🖨️ Reimprimir Ticket", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnImprimir, btnCerrar);
        dialog.getDialogPane().setPrefWidth(900);

        VBox mainLayout = new VBox(15);
        mainLayout.setPadding(new javafx.geometry.Insets(20));

        // --- SECCIÓN: INFO GENERAL Y CLIENTE ---
        HBox infoSuperior = new HBox(40); // Espacio entre info factura e info cliente

        // Bloque Izquierdo: Datos Factura
        GridPane facturaGrid = new GridPane();
        facturaGrid.setHgap(10); facturaGrid.setVgap(8);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        facturaGrid.add(crearLabelTitulo("DATOS FACTURA"), 0, 0, 2, 1);
        facturaGrid.add(new Label("Fecha:"), 0, 1);
        facturaGrid.add(new Label(factura.getFechaEmision().format(formatter)), 1, 1);
        facturaGrid.add(new Label("Atendido por:"), 0, 2);
        facturaGrid.add(new Label(factura.getEmpleadoNombre() != null ? factura.getEmpleadoNombre() : "Admin"), 1, 2);
        facturaGrid.add(new Label("Forma de pago:"), 0, 3);
        facturaGrid.add(new Label(factura.getMetodoPago() != null ? factura.getMetodoPago() : "EFECTIVO"), 1, 3);
        if (factura.getFechaModificacion() != null) {
            facturaGrid.add(new Label("Última modificación:"), 0, 4);
            facturaGrid.add(new Label(factura.getFechaModificacion().format(formatter)), 1, 4);
        }

        // Bloque Derecho: Datos Cliente
        GridPane clienteGrid = new GridPane();
        clienteGrid.setHgap(10); clienteGrid.setVgap(8);
        clienteGrid.setStyle("-fx-background-color: #f4f4f4; -fx-padding: 10; -fx-background-radius: 5; -fx-border-color: #ddd; -fx-border-radius: 5;");

        clienteGrid.add(crearLabelTitulo("DATOS DEL CLIENTE"), 0, 0, 2, 1);

        if (clienteCompleto != null) {
            clienteGrid.add(new Label("Nombre:"), 0, 1);
            clienteGrid.add(new Label(clienteCompleto.getNombre()), 1, 1);
            clienteGrid.add(new Label("DNI/NIF:"), 0, 2);
            clienteGrid.add(new Label(clienteCompleto.getDni()), 1, 2);
            clienteGrid.add(new Label("Teléfono:"), 0, 3);
            clienteGrid.add(new Label(clienteCompleto.getTelefono() != null ? clienteCompleto.getTelefono() : "-"), 1, 3);
            clienteGrid.add(new Label("Dirección:"), 0, 4);
            clienteGrid.add(new Label(clienteCompleto.getDireccion() != null ? clienteCompleto.getDireccion() : "-"), 1, 4);
            clienteGrid.add(new Label("Email:"), 0, 5);
            clienteGrid.add(new Label(clienteCompleto.getEmail() != null ? clienteCompleto.getEmail() : "-"), 1, 5);
        } else {
            clienteGrid.add(new Label("Cliente:"), 0, 1);
            clienteGrid.add(new Label(factura.getClienteNombre() != null ? factura.getClienteNombre() : "AL CONTADO"), 1, 1);
            clienteGrid.add(new Label("Nota:"), 0, 2);
            clienteGrid.add(new Label("Sin datos de registro adicionales"), 1, 2);
        }

        infoSuperior.getChildren().addAll(facturaGrid, clienteGrid);
        HBox.setHgrow(clienteGrid, javafx.scene.layout.Priority.ALWAYS);

        if (factura.isAnulada()) {
            Label banner = new Label("⛔ FACTURA ANULADA"
                    + (factura.getFechaAnulacion() != null ? " el " + factura.getFechaAnulacion().format(formatter) : "")
                    + (factura.getAnuladaPor() != null ? " por " + factura.getAnuladaPor() : "")
                    + "\nMotivo: " + (factura.getMotivoAnulacion() != null ? factura.getMotivoAnulacion() : "-"));
            banner.setWrapText(true);
            banner.setMaxWidth(Double.MAX_VALUE);
            banner.setStyle("-fx-background-color: #fdecea; -fx-text-fill: #c0392b; -fx-font-weight: bold; "
                    + "-fx-padding: 10; -fx-background-radius: 5; -fx-border-color: #e74c3c; -fx-border-radius: 5;");
            mainLayout.getChildren().add(banner);
        }

        mainLayout.getChildren().addAll(infoSuperior, new Separator());

        // --- TABLA DE LÍNEAS
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
        tablaLineas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); // Para que use todo el ancho
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

        Button btnHistorial = new Button("📜 Historial de cambios");
        btnHistorial.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-cursor: hand;");
        btnHistorial.setOnAction(e -> verHistorial(factura));
        Region espacio = new Region();
        HBox.setHgrow(espacio, javafx.scene.layout.Priority.ALWAYS);

        footer.getChildren().addAll(btnHistorial, espacio, labels, values);
        mainLayout.getChildren().add(footer);

        // Una factura anulada no es un documento válido: no se puede reimprimir
        if (factura.isAnulada()) {
            dialog.getDialogPane().lookupButton(btnImprimir).setDisable(true);
        }

        dialog.getDialogPane().setContent(mainLayout);
        dialog.setResultConverter(b -> {
            if (b == btnImprimir) reimprimirTicket(factura);
            return null;
        });
        dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    // EDITAR / ANULAR / RESTAURAR
    // ------------------------------------------------------------------

    private void editarFactura(Factura factura) {
        Optional<EditarFacturaDialog.Resultado> resultado =
                new EditarFacturaDialog(factura, facturaService).mostrar();
        if (resultado.isEmpty()) {
            return;
        }
        try {
            facturaService.actualizarFactura(resultado.get().factura, resultado.get().motivo);
            refrescarFactura(factura.getId());
            mostrarInfo("Factura modificada",
                    "La factura " + factura.getNumeroFactura() + " se ha actualizado correctamente.\n"
                            + "El cambio queda registrado en su historial.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            mostrarAlerta("No se pudo guardar", e.getMessage());
        } catch (FacturaRepository.ConflictoConcurrenciaException e) {
            mostrarAlerta("Conflicto de edición", e.getMessage());
            cargarFacturas();
        } catch (SQLException e) {
            mostrarError("Error", "No se pudo guardar la factura: " + e.getMessage());
        }
    }

    /**
     * Las facturas NO se borran: se anulan. Así su número permanece reservado y el
     * resto de la numeración (tickets anteriores y posteriores) no se altera.
     */
    private void anularFactura(Factura factura) {
        Optional<String> motivo = pedirMotivo(
                "Anular factura",
                "Anular la factura " + factura.getNumeroFactura(),
                "La factura " + factura.getNumeroFactura() + " (" + String.format("%.2f €", factura.getTotalConIva())
                        + ") NO se eliminará de la base de datos.\n\n"
                        + "• Quedará marcada como ANULADA y conservará su número, por lo que el resto de la "
                        + "numeración no se ve alterada y no se reutilizará.\n"
                        + "• Dejará de contar en los totales, y no podrá modificarse ni reimprimirse.\n"
                        + "• Podrás restaurarla si te has equivocado.",
                "🚫 Anular factura",
                "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        if (motivo.isEmpty()) {
            return;
        }
        try {
            facturaService.anularFactura(factura.getId(), motivo.get());
            refrescarFactura(factura.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            mostrarAlerta("No se pudo anular", e.getMessage());
            cargarFacturas();
        } catch (FacturaRepository.ConflictoConcurrenciaException e) {
            mostrarAlerta("Conflicto de edición", e.getMessage());
            cargarFacturas();
        } catch (SQLException e) {
            mostrarError("Error", "No se pudo anular la factura: " + e.getMessage());
        }
    }

    private void restaurarFactura(Factura factura) {
        Optional<String> motivo = pedirMotivo(
                "Restaurar factura",
                "Restaurar la factura " + factura.getNumeroFactura(),
                "La factura volverá a estar EMITIDA con su mismo número y volverá a contar en los totales.\n\n"
                        + "Anulada por: " + (factura.getAnuladaPor() != null ? factura.getAnuladaPor() : "-") + "\n"
                        + "Motivo de la anulación: "
                        + (factura.getMotivoAnulacion() != null ? factura.getMotivoAnulacion() : "-"),
                "↩ Restaurar factura",
                "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        if (motivo.isEmpty()) {
            return;
        }
        try {
            facturaService.restaurarFactura(factura.getId(), motivo.get());
            refrescarFactura(factura.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            mostrarAlerta("No se pudo restaurar", e.getMessage());
            cargarFacturas();
        } catch (FacturaRepository.ConflictoConcurrenciaException e) {
            mostrarAlerta("Conflicto de edición", e.getMessage());
            cargarFacturas();
        } catch (SQLException e) {
            mostrarError("Error", "No se pudo restaurar la factura: " + e.getMessage());
        }
    }

    /**
     * Diálogo de confirmación con un motivo opcional. Devuelve vacío si se cancela;
     * si se confirma sin escribir nada, devuelve una cadena vacía.
     */
    private Optional<String> pedirMotivo(String titulo, String cabecera, String explicacion,
                                         String textoBoton, String estiloBoton) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(titulo);
        dialog.setHeaderText(cabecera);

        ButtonType btnConfirmar = new ButtonType(textoBoton, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnConfirmar, ButtonType.CANCEL);

        Label lblExplicacion = new Label(explicacion);
        lblExplicacion.setWrapText(true);
        lblExplicacion.setPrefWidth(480);

        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Motivo (opcional)");

        VBox contenido = new VBox(12, lblExplicacion, new Label("Motivo (opcional):"), txtMotivo);
        contenido.setPadding(new javafx.geometry.Insets(15));
        dialog.getDialogPane().setContent(contenido);

        Node botonOk = dialog.getDialogPane().lookupButton(btnConfirmar);
        botonOk.setStyle(estiloBoton);

        dialog.setResultConverter(b -> b == btnConfirmar ? txtMotivo.getText().trim() : null);
        javafx.application.Platform.runLater(txtMotivo::requestFocus);
        return dialog.showAndWait();
    }

    /**
     * Recarga una única factura desde la BD y la sustituye en la tabla, sin perder
     * los filtros que el usuario tuviera aplicados.
     */
    private void refrescarFactura(Long id) {
        try {
            Factura fresca = facturaService.obtenerPorId(id);
            for (int i = 0; i < facturasObservables.size(); i++) {
                if (facturasObservables.get(i).getId().equals(id)) {
                    if (fresca != null) {
                        facturasObservables.set(i, fresca);
                    }
                    break;
                }
            }
            FXCollections.sort(facturasObservables, ORDEN_FECHA_DESC); // la fecha pudo cambiar
            actualizarEstadisticas();
        } catch (SQLException e) {
            mostrarError("Error", "No se pudo actualizar la lista: " + e.getMessage());
        }
    }

    private void verHistorial(Factura factura) {
        List<RegistroAuditoria> registros;
        try {
            registros = facturaService.obtenerHistorial(factura.getId());
        } catch (SQLException e) {
            mostrarError("Error", "No se pudo cargar el historial: " + e.getMessage());
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Historial de cambios");
        dialog.setHeaderText("Historial de la factura " + factura.getNumeroFactura());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        ListView<RegistroAuditoria> lista = new ListView<>(FXCollections.observableArrayList(registros));
        lista.setPrefSize(760, 380);
        lista.setPlaceholder(new Label("Esta factura no ha sido modificada desde su emisión"));
        lista.setCellFactory(lv -> new ListCell<RegistroAuditoria>() {
            @Override
            protected void updateItem(RegistroAuditoria r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) {
                    setGraphic(null);
                    return;
                }
                Label cabecera = new Label(r.getFecha().format(fmt) + "  ·  " + r.getAccion()
                        + "  ·  " + (r.getUsuario() != null ? r.getUsuario() : "-"));
                cabecera.setStyle("-fx-font-weight: bold;");
                Label motivo = new Label("Motivo: " + (r.getMotivo() != null ? r.getMotivo() : "(no indicado)"));
                motivo.setWrapText(true);
                motivo.setMaxWidth(700);
                Label detalle = new Label(r.getDetalle() != null ? r.getDetalle() : "");
                detalle.setWrapText(true);
                detalle.setMaxWidth(700);
                detalle.setStyle("-fx-text-fill: #555;");
                VBox caja = new VBox(3, cabecera, motivo, detalle);
                caja.setPadding(new javafx.geometry.Insets(6, 4, 6, 4));
                setGraphic(caja);
            }
        });
        dialog.getDialogPane().setContent(lista);
        dialog.showAndWait();
    }

    // Método auxiliar para dar formato a los títulos de las secciones
    private Label crearLabelTitulo(String texto) {
        Label label = new Label(texto);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #2980b9; -fx-underline: true;");
        return label;
    }


    private void reimprimirTicket(Factura factura) {
        try {
            if (impresoraService != null) impresoraService.imprimirTicketConSeleccion(factura);
            else mostrarError("Error", "Servicio de impresión no disponible.");
        } catch (Exception e) {
            mostrarError("Error", "Servicio de impresión no disponible." + e.getMessage());
        }
    }

    private void actualizarEstadisticas() {
        // Las facturas anuladas NO computan en el recuento ni en el importe
        List<Factura> validas = facturasObservables.stream().filter(f -> !f.isAnulada()).toList();
        long anuladas = facturasObservables.size() - validas.size();

        lblTotalFacturas.setText(validas.size() + " facturas"
                + (anuladas > 0 ? " (" + anuladas + " anulada" + (anuladas == 1 ? "" : "s") + " no computada"
                + (anuladas == 1 ? "" : "s") + ")" : ""));
        BigDecimal sumaTotal = validas.stream()
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

    private void mostrarInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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
