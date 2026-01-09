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
import javafx.scene.layout.GridPane;
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.tpv.config.Configuracion;
import org.tpv.domain.Cliente;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;
import org.tpv.domain.Producto;
import org.tpv.repository.ClienteRepository;
import org.tpv.repository.ConfiguracionRepository;
import org.tpv.service.ImpresoraService;
import org.tpv.service.ProductoService;
import org.tpv.service.VentaService;

import java.math.BigDecimal;
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
    @FXML private TableColumn<LineaFactura, String> colCodigo;
    @FXML private TableColumn<LineaFactura, String> colNombre;
    @FXML private TableColumn<LineaFactura, Integer> colCantidad;
    @FXML private TableColumn<LineaFactura, BigDecimal> colPrecio;
    @FXML private TableColumn<LineaFactura, BigDecimal> colSubtotal;
    @FXML private TableColumn<LineaFactura, Integer> colDescuento;

    @FXML private Label lblTotalSinIva;
    @FXML private Label lblIva;
    @FXML private Label lblTotal;
    @FXML private Label lblClienteActual;
    @FXML private Button btnSeleccionarCliente;

    // ===== SERVICIOS Y DATOS =====
    private ProductoService productoService;
    private VentaService ventaService;
    private Configuracion config;
    private ImpresoraService impresoraService;
    private ConfiguracionRepository configRepository;
    private ClienteRepository clienteRepository;


    private ObservableList<LineaFactura> lineasObservables = FXCollections.observableArrayList();

    // ===== INICIALIZACIÓN =====
    @FXML
    public void initialize() {
        System.out.println("✓ Inicializando VentaController...");

        try {
            // Inicializar repositorios y servicios
            productoService = new ProductoService();
            configRepository = new ConfiguracionRepository();
            clienteRepository = new ClienteRepository();

            // Cargar configuración desde la base de datos
            config = configRepository.obtenerConfiguracion();
            System.out.println("✓ Configuración cargada: " + config.getNombreTienda());

            ventaService = new VentaService(config);
            ventaService.iniciarVenta();

            impresoraService = new ImpresoraService(config);

            // Configurar las columnas de la tabla
            configurarTabla();

            // Focus en el campo de código
            txtCodigoBarra.requestFocus();

            iniciarReloj();

            // Si lblUsuario existe, establecer el nombre de usuario
            if (lblUsuario != null) {
                lblUsuario.setText("Usuario: Admin");
            }
            // Inicializar label de cliente
            if (lblClienteActual != null) {
                actualizarLabelCliente();
            }

            // ===== OPCIONAL: AÑADIR ATAJOS DE TECLADO =====
            // Añadir al final del método initialize():
            configurarAtajosTeclado();

            System.out.println("✓ VentaController inicializado correctamente");

        } catch (Exception e) {
            System.err.println("❌ Error al inicializar VentaController: " + e.getMessage());
            e.printStackTrace();
            mostrarError("Error de inicialización",
                    "No se pudo cargar la configuración: " + e.getMessage());
        }
    }

    /**
     * Inicia un hilo para actualizar la fecha y hora cada segundo
     */
    private void iniciarReloj() {
        Thread reloj = new Thread(() -> {
            while (true) {
                try {
                    javafx.application.Platform.runLater(() -> {
                        if (lblFechaHora != null) {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                            lblFechaHora.setText(java.time.LocalDateTime.now().format(formatter));
                        }
                    });
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        reloj.setDaemon(true);
        reloj.start();
    }


    private void configurarTabla() {
        // ⭐ HACER LA TABLA COMPLETAMENTE EDITABLE ⭐
        tablaTicket.setEditable(true);

        // ========== COLUMNA CÓDIGO (EDITABLE) ==========
        colCodigo.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getCodigoProducto()));

        colCodigo.setCellFactory(TextFieldTableCell.forTableColumn());

        colCodigo.setOnEditCommit(event -> {
            LineaFactura linea = event.getRowValue();
            String nuevoCodigo = event.getNewValue();

            if (nuevoCodigo == null || nuevoCodigo.trim().isEmpty()) {
                mostrarAlerta("Código inválido", "El código no puede estar vacío");
                tablaTicket.refresh();
                return;
            }

            linea.setCodigoProducto(nuevoCodigo.trim());
            System.out.println("✓ Código modificado: " + linea.getNombreProducto() + " -> " + nuevoCodigo);
        });

        // ========== COLUMNA NOMBRE (EDITABLE) ==========
        colNombre.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNombreProducto()));

        colNombre.setCellFactory(TextFieldTableCell.forTableColumn());

        colNombre.setOnEditCommit(event -> {
            LineaFactura linea = event.getRowValue();
            String nuevoNombre = event.getNewValue();

            if (nuevoNombre == null || nuevoNombre.trim().isEmpty()) {
                mostrarAlerta("Nombre inválido", "El nombre no puede estar vacío");
                tablaTicket.refresh();
                return;
            }

            linea.setNombreProducto(nuevoNombre.trim());
            System.out.println("✓ Nombre modificado: " + nuevoNombre);
        });

        // ========== COLUMNA CANTIDAD (EDITABLE) ==========
        colCantidad.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getCantidad()).asObject());

        colCantidad.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));

        colCantidad.setOnEditCommit(event -> {
            LineaFactura linea = event.getRowValue();
            Integer nuevaCantidad = event.getNewValue();

            if (nuevaCantidad == null || nuevaCantidad <= 0) {
                mostrarAlerta("Cantidad inválida", "La cantidad debe ser mayor que 0");
                tablaTicket.refresh();
                return;
            }

            linea.setCantidad(nuevaCantidad);
            ventaService.finalizarVenta().recalcularTotales();
            actualizarVista();

        });

        // ========== COLUMNA PRECIO (EDITABLE) ==========
        colPrecio.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue().getPrecioUnitario()));

        colPrecio.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));

        colPrecio.setOnEditCommit(event -> {
            LineaFactura linea = event.getRowValue();
            BigDecimal nuevoPrecio = event.getNewValue();

            if (nuevoPrecio == null || nuevoPrecio.compareTo(BigDecimal.ZERO) < 0) {
                mostrarAlerta("Precio inválido", "El precio no puede ser negativo");
                tablaTicket.refresh();
                return;
            }

            linea.setPrecioUnitario(nuevoPrecio);
            ventaService.finalizarVenta().recalcularTotales();
            actualizarVista();

            System.out.println("✓ Precio modificado: " + linea.getNombreProducto() + " -> " + nuevoPrecio + "€");
        });

        // ========== COLUMNA DESCUENTO (EDITABLE) ==========
        colDescuento.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getDescuento()).asObject());

        colDescuento.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));

        colDescuento.setOnEditCommit(event -> {
            LineaFactura linea = event.getRowValue();
            Integer nuevoDescuento = event.getNewValue();

            // Si se borra el valor, se pone a 0 automáticamente
            if (nuevoDescuento == null) {
                nuevoDescuento = 0;
            }

            // Validación de rango
            if (nuevoDescuento < 0 || nuevoDescuento > 100) {
                mostrarAlerta("Descuento inválido", "El descuento debe estar entre 0 y 100");
                tablaTicket.refresh();
                return;
            }

            linea.setDescuento(nuevoDescuento);
            ventaService.finalizarVenta().recalcularTotales();
            actualizarVista();

            System.out.println("✓ Descuento modificado: " + linea.getNombreProducto() + " -> " + nuevoDescuento + "%");
        });

        // Formatear la columna de descuento
        colDescuento.setCellFactory(col -> new TableCell<LineaFactura, Integer>() {
            @Override
            protected void updateItem(Integer descuento, boolean empty) {
                super.updateItem(descuento, empty);
                if (empty || descuento == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(descuento + "%");
                    // Resaltar si hay descuento aplicado
                    if (descuento > 0) {
                        setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Aplicar cell factory editable después del formato
        colDescuento.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        colDescuento.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));

        // ========== COLUMNA SUBTOTAL (SOLO LECTURA - CALCULADO) ==========
        colSubtotal.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue().getTotalConIva()));

        // Formatear columnas de dinero para mostrar
        colPrecio.setCellFactory(col -> new TableCell<LineaFactura, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal precio, boolean empty) {
                super.updateItem(precio, empty);
                if (empty || precio == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f€", precio));
                }
            }
        });

        // Aplicar el cell factory editable para precio
        colPrecio.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));

        colSubtotal.setCellFactory(col -> new TableCell<LineaFactura, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal subtotal, boolean empty) {
                super.updateItem(subtotal, empty);
                if (empty || subtotal == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f€", subtotal));
                }
                // Destacar visualmente que NO es editable
                if (!empty) {
                    setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #666;");
                }
            }
        });

        // Vincular datos observables a la tabla
        tablaTicket.setItems(lineasObservables);
    }

    // ===== EVENTO: AÑADIR PRODUCTO =====
    @FXML
    private void onAñadirProducto() {
        String codigo = txtCodigoBarra.getText().trim();

        // Si el campo está vacío, permitir crear producto sin código
        if (codigo.isEmpty()) {
            try {
                Producto producto = mostrarDialogoCrearProductoSinCodigo();
                if (producto != null) {
                    ventaService.añadirProducto(producto);
                    System.out.println("✓ Producto añadido: " + producto.getNombre());
                    actualizarVista();
                }
                txtCodigoBarra.clear();
                txtCodigoBarra.requestFocus();
            } catch (SQLException e) {
                mostrarError("Error de base de datos",
                        "No se pudo procesar el producto: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        try {
            Producto producto = productoService.buscarPorCodigo(codigo);

            if (producto == null) {
                System.out.println("⚠ Producto no encontrado: " + codigo);
                producto = mostrarDialogoCrearProducto(codigo);

                if (producto == null) {
                    txtCodigoBarra.clear();
                    txtCodigoBarra.requestFocus();
                    return;
                }
            }

            ventaService.añadirProducto(producto);
            System.out.println("✓ Producto añadido: " + producto.getNombre());

            actualizarVista();

            txtCodigoBarra.clear();
            txtCodigoBarra.requestFocus();

        } catch (SQLException e) {
            mostrarError("Error de base de datos",
                    "No se pudo procesar el producto: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // ===== EVENTO: INCREMENTAR CANTIDAD (+1) =====
    @FXML
    private void onIncrementar() {
        LineaFactura lineaSeleccionada = tablaTicket.getSelectionModel().getSelectedItem();

        if (lineaSeleccionada == null) {
            mostrarAlerta("Ninguna línea seleccionada",
                    "Selecciona una línea de la tabla para incrementar su cantidad");
            return;
        }

        lineaSeleccionada.setCantidad(lineaSeleccionada.getCantidad() + 1);
        ventaService.finalizarVenta().recalcularTotales();
        actualizarVista();

        System.out.println("✓ Cantidad incrementada: " + lineaSeleccionada.getNombreProducto()
                + " -> " + lineaSeleccionada.getCantidad());
    }

    // ===== EVENTO: DECREMENTAR CANTIDAD (-1) =====
    @FXML
    private void onDecrementar() {
        LineaFactura lineaSeleccionada = tablaTicket.getSelectionModel().getSelectedItem();

        if (lineaSeleccionada == null) {
            mostrarAlerta("Ninguna línea seleccionada",
                    "Selecciona una línea de la tabla para decrementar su cantidad");
            return;
        }

        int cantidadActual = lineaSeleccionada.getCantidad();

        if (cantidadActual == 1) {
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
            confirmacion.setTitle("Eliminar línea");
            confirmacion.setHeaderText("La cantidad es 1");
            confirmacion.setContentText("¿Deseas eliminar esta línea del ticket?");

            Optional<ButtonType> resultado = confirmacion.showAndWait();
            if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
                onEliminarLinea();
            }
            return;
        }

        lineaSeleccionada.setCantidad(cantidadActual - 1);
        ventaService.finalizarVenta().recalcularTotales();
        actualizarVista();

        System.out.println("✓ Cantidad decrementada: " + lineaSeleccionada.getNombreProducto()
                + " -> " + lineaSeleccionada.getCantidad());
    }

    // ===== EVENTO: ELIMINAR LÍNEA =====
    @FXML
    private void onEliminarLinea() {
        LineaFactura lineaSeleccionada = tablaTicket.getSelectionModel().getSelectedItem();

        if (lineaSeleccionada == null) {
            mostrarAlerta("Ninguna línea seleccionada",
                    "Selecciona una línea de la tabla para eliminarla");
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar línea");
        confirmacion.setHeaderText("¿Eliminar este producto?");
        confirmacion.setContentText(lineaSeleccionada.getNombreProducto() +
                " (x" + lineaSeleccionada.getCantidad() + ")");

        Optional<ButtonType> resultado = confirmacion.showAndWait();
        if (resultado.isEmpty() || resultado.get() != ButtonType.OK) {
            return;
        }

        Factura factura = ventaService.finalizarVenta();
        factura.getLineas().remove(lineaSeleccionada);
        factura.recalcularTotales();

        actualizarVista();

        System.out.println("✓ Línea eliminada: " + lineaSeleccionada.getNombreProducto());
    }

    // ===== DIÁLOGO: CREAR NUEVO PRODUCTO =====
    private Producto mostrarDialogoCrearProducto(String codigo) throws SQLException {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.setTitle("Producto no encontrado");
        dialog.setHeaderText("El código '" + codigo + "' no existe en la base de datos.\n¿Deseas crear este producto?");

        ButtonType btnCrear = new ButtonType("Crear", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCrear, btnCancelar);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre del producto");

        TextField txtPrecio = new TextField();
        txtPrecio.setPromptText("Precio (con IVA)");
        txtPrecio.setText("0.00");

        grid.add(new Label("Código:"), 0, 0);
        grid.add(new Label(codigo), 1, 0);
        grid.add(new Label("Nombre:"), 0, 1);
        grid.add(txtNombre, 1, 1);
        grid.add(new Label("Precio:"), 0, 2);
        grid.add(txtPrecio, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Node botonCrear = dialog.getDialogPane().lookupButton(btnCrear);
        botonCrear.setDisable(true);

        // VALIDACIÓN EN TIEMPO REAL
        ChangeListener<String> validador = (obs, oldVal, newVal) -> {

            String nombre = txtNombre.getText().trim();
            String precioStr = txtPrecio.getText().trim().replace(",", ".");

            boolean nombreValido = !nombre.isEmpty();
            boolean precioValido;

            try {
                BigDecimal precio = new BigDecimal(precioStr);
                precioValido = precio.compareTo(BigDecimal.ZERO) > 0;
            } catch (Exception e) {
                precioValido = false;
            }

            botonCrear.setDisable(!(nombreValido && precioValido));
        };
        txtNombre.textProperty().addListener(validador);
        txtPrecio.textProperty().addListener(validador);

        javafx.application.Platform.runLater(() -> txtNombre.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnCrear) {
                try {
                    String nombre = txtNombre.getText().trim();
                    String precioStr = txtPrecio.getText().trim().replace(",", ".");

                    BigDecimal precio = new BigDecimal(precioStr);

                    Producto nuevoProducto = productoService.crearProducto(codigo, nombre, precio);
                    System.out.println("✓ Producto creado: " + nuevoProducto.getNombre()
                            + " - " + nuevoProducto.getPrecioBase() + "€");

                    return nuevoProducto;

                } catch (SQLException e) {
                    mostrarError("Error al crear producto", e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        Optional<Producto> resultado = dialog.showAndWait();
        return resultado.orElse(null);
    }

    /**
     * Muestra diálogo para crear producto sin código de barras
     */
    private Producto mostrarDialogoCrearProductoSinCodigo() throws SQLException {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.setTitle("Crear producto sin código");
        dialog.setHeaderText("Introduce los datos del producto");

        ButtonType btnCrear = new ButtonType("Crear", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCrear, btnCancelar);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField txtCodigo = new TextField();
        txtCodigo.setPromptText("Opcional - Código o referencia");

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre del producto");

        TextField txtPrecio = new TextField();
        txtPrecio.setPromptText("Precio con IVA");
        txtPrecio.setText("0.00");

        grid.add(new Label("Código:"), 0, 0);
        grid.add(txtCodigo, 1, 0);
        grid.add(new Label("Nombre:*"), 0, 1);
        grid.add(txtNombre, 1, 1);
        grid.add(new Label("Precio:*"), 0, 2);
        grid.add(txtPrecio, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Node botonCrear = dialog.getDialogPane().lookupButton(btnCrear);
        botonCrear.setDisable(true);

        // VALIDACIÓN EN TIEMPO REAL
        ChangeListener<String> validador = (obs, oldVal, newVal) -> {
            String nombre = txtNombre.getText().trim();
            String precioStr = txtPrecio.getText().trim().replace(",", ".");

            boolean nombreValido = !nombre.isEmpty();
            boolean precioValido;

            try {
                BigDecimal precio = new BigDecimal(precioStr);
                precioValido = precio.compareTo(BigDecimal.ZERO) > 0;
            } catch (Exception e) {
                precioValido = false;
            }

            botonCrear.setDisable(!(nombreValido && precioValido));
        };
        txtNombre.textProperty().addListener(validador);
        txtPrecio.textProperty().addListener(validador);

        javafx.application.Platform.runLater(() -> txtNombre.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnCrear) {
                try {
                    String codigo = txtCodigo.getText().trim();
                    String nombre = txtNombre.getText().trim();
                    String precioStr = txtPrecio.getText().trim().replace(",", ".");

                    // Si no hay código, generar uno automático
                    if (codigo.isEmpty()) {
                        codigo = "PROD-" + System.currentTimeMillis();
                    }

                    BigDecimal precio = new BigDecimal(precioStr);

                    Producto nuevoProducto = productoService.crearProducto(codigo, nombre, precio);
                    System.out.println("✓ Producto creado: " + nuevoProducto.getNombre()
                            + " - " + nuevoProducto.getPrecioBase() + "€");

                    return nuevoProducto;

                } catch (SQLException e) {
                    mostrarError("Error al crear producto", e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        Optional<Producto> resultado = dialog.showAndWait();
        return resultado.orElse(null);
    }


    // ===== EVENTO: NUEVA VENTA =====
    @FXML
    private void onNuevaVenta() {
        if (!lineasObservables.isEmpty()) {
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
            confirmacion.setTitle("Nueva venta");
            confirmacion.setHeaderText("¿Deseas iniciar una nueva venta?");
            confirmacion.setContentText("Se perderá la venta actual si no ha sido cobrada");

            Optional<ButtonType> resultado = confirmacion.showAndWait();
            if (resultado.isEmpty() || resultado.get() != ButtonType.OK) {
                return;
            }
        }

        ventaService.iniciarVenta();
        lineasObservables.clear();
        actualizarTotales(new Factura());
        txtCodigoBarra.clear();
        txtCodigoBarra.requestFocus();

        System.out.println("✓ Nueva venta iniciada");
    }

    // ===== EVENTO: COBRAR =====
    @FXML
    private void onCobrar() {
        if (lineasObservables.isEmpty()) {
            mostrarAlerta("Venta vacía", "Debes añadir productos antes de cobrar");
            return;
        }

        Factura factura = ventaService.finalizarVenta();
        try {
            ventaService.guardarVenta();
            System.out.println("✓ Factura guardada en la BD: " + factura.getNumeroFactura());
        } catch (SQLException e) {
            mostrarError("Error al guardar la factura",
                    "No se pudo guardar la venta en la base de datos:\n" + e.getMessage());
            e.printStackTrace();
            return;
        }


        // ACTUALIZAR PRODUCTOS EN LA BD CON LOS CAMBIOS
        actualizarProductosEnBD(factura);

        // ACTUALIZAR CLIENTE EN LA BD SI HUBO CAMBIOS
        actualizarClienteEnBD();

        // MOSTRAR DIÁLOGO DE CONFIRMACIÓN CON OPCIÓN DE IMPRIMIR
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Cobro realizado");
        confirmacion.setHeaderText("✓ Venta finalizada correctamente");

        String clienteInfo = factura.getClienteNombre() != null ?
                "\nCliente: " + factura.getClienteNombre() : "";

        confirmacion.setContentText(String.format(
                """
                ═══════════════════════════════
                Factura: %s%s
                ═══════════════════════════════
                Base imponible: %.2f€
                IVA (21%%):      %.2f€
                ───────────────────────────────
                TOTAL:          %.2f€
                ═══════════════════════════════
                
                Artículos: %d
                
                ¿Deseas imprimir el ticket?
                """,
                factura.getNumeroFactura(),
                clienteInfo,
                factura.getTotalSinIva(),
                factura.getTotalIva(),
                factura.getTotalConIva(),
                factura.getLineas().size()
        ));

        ButtonType btnImprimir = new ButtonType("Imprimir");
        ButtonType btnNoImprimir = new ButtonType("No imprimir");
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        confirmacion.getButtonTypes().setAll(btnImprimir, btnNoImprimir, btnCancelar);

        Optional<ButtonType> resultado = confirmacion.showAndWait();

        if (resultado.isPresent()) {
            if (resultado.get() == btnImprimir) {
                imprimirTicket(factura);
            } else if (resultado.get() == btnNoImprimir) {
                System.out.println("✓ Venta cobrada sin imprimir: " + factura.getTotalConIva() + "€");
            } else {
                // Cancelar - no hacer nada, mantener la venta actual
                return;
            }
        }

        System.out.println("✓ Venta cobrada: " + factura.getTotalConIva() + "€");
        onNuevaVenta();
    }


    // ===== ACTUALIZAR PRODUCTOS EN LA BD =====
    private void actualizarProductosEnBD(Factura factura) {
        for (LineaFactura linea : factura.getLineas()) {
            try {
                // Buscar el producto en la BD
                Producto producto = productoService.buscarPorCodigo(linea.getCodigoProducto());

                if (producto != null) {
                    // Verificar si hubo cambios
                    boolean cambios = false;

                    if (!producto.getNombre().equals(linea.getNombreProducto())) {
                        producto.setNombre(linea.getNombreProducto());
                        cambios = true;
                    }

                    if (producto.getPrecioBase().compareTo(linea.getPrecioUnitario()) != 0) {
                        producto.setPrecioBase(linea.getPrecioUnitario());
                        cambios = true;
                    }

                    // Si hubo cambios, actualizar en la BD
                    if (cambios) {
                        productoService.actualizarProducto(producto);
                        System.out.println("✓ Producto actualizado en BD: " + producto.getNombre());
                    }
                }
            } catch (SQLException e) {
                System.err.println("⚠ Error actualizando producto: " + linea.getCodigoProducto());
                e.printStackTrace();
            }
        }
    }

    private void imprimirTicket(Factura factura) {
        // Obtener impresoras disponibles
        String[] impresoras = impresoraService.obtenerImpresorasDisponibles();

        if (impresoras.length == 0) {
            mostrarError("Sin impresoras",
                    "No se detectaron impresoras en el sistema.\n" +
                            "Verifica que la impresora esté conectada y los drivers instalados.");
            return;
        }

        // Si solo hay una impresora, usarla directamente
        if (impresoras.length == 1) {
            try {
                impresoraService.imprimirTicket(factura, impresoras[0]);

                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Impresión exitosa");
                info.setHeaderText("Ticket impreso correctamente");
                info.setContentText("Impresora: " + impresoras[0]);
                info.showAndWait();

                System.out.println("✓ Ticket impreso en: " + impresoras[0]);

            } catch (Exception e) {
                mostrarError("Error al imprimir",
                        "No se pudo imprimir el ticket:\n" + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        // Si hay múltiples impresoras, mostrar diálogo de selección
        ChoiceDialog<String> dialog = new ChoiceDialog<>(impresoras[0], impresoras);
        dialog.setTitle("Seleccionar impresora");
        dialog.setHeaderText("Selecciona la impresora para el ticket");
        dialog.setContentText("Impresora:");

        Optional<String> seleccion = dialog.showAndWait();

        if (seleccion.isPresent()) {
            try {
                impresoraService.imprimirTicket(factura, seleccion.get());

                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Impresión exitosa");
                info.setHeaderText("Ticket impreso correctamente");
                info.setContentText("Impresora: " + seleccion.get());
                info.showAndWait();

                System.out.println("✓ Ticket impreso en: " + seleccion.get());

            } catch (Exception e) {
                mostrarError("Error al imprimir",
                        "No se pudo imprimir el ticket:\n" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // ===== MÉTODOS AUXILIARES =====

    /**
     * Abre el diálogo para buscar y seleccionar un cliente
     */
    @FXML
    private void onSeleccionarCliente() {
        try {
            // Crear diálogo de opciones
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Seleccionar Cliente");
            alert.setHeaderText("¿Qué deseas hacer?");

            ButtonType btnBuscar = new ButtonType("Buscar cliente");
            ButtonType btnNuevo = new ButtonType("Nuevo cliente");
            ButtonType btnContado = new ButtonType("Cliente al contado");
            ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnBuscar, btnNuevo, btnContado, btnCancelar);

            Optional<ButtonType> resultado = alert.showAndWait();

            if (resultado.isPresent()) {
                if (resultado.get() == btnBuscar) {
                    buscarCliente();
                } else if (resultado.get() == btnNuevo) {
                    crearNuevoCliente();
                } else if (resultado.get() == btnContado) {
                    ventaService.asignarCliente(Cliente.clientePorDefecto());
                    actualizarLabelCliente();
                }
            }

        } catch (Exception e) {
            mostrarError("Error", "No se pudo gestionar el cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Busca un cliente por DNI o nombre
     */
    private void buscarCliente() throws SQLException {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Buscar Cliente");
        dialog.setHeaderText("Introduce el DNI o nombre del cliente");
        dialog.setContentText("Búsqueda:");

        Optional<String> resultado = dialog.showAndWait();

        if (resultado.isPresent() && !resultado.get().trim().isEmpty()) {
            String busqueda = resultado.get().trim();

            // Intentar buscar por DNI primero
            Cliente cliente = clienteRepository.findByDni(busqueda);

            // Si no se encuentra por DNI, buscar por nombre
            if (cliente == null) {
                List<Cliente> clientes = clienteRepository.findByNombre(busqueda);

                if (clientes.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("No encontrado");
                    alert.setHeaderText("Cliente no encontrado");
                    alert.setContentText("¿Deseas crear un nuevo cliente?");

                    ButtonType btnSi = new ButtonType("Sí");
                    ButtonType btnNo = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(btnSi, btnNo);

                    Optional<ButtonType> respuesta = alert.showAndWait();
                    if (respuesta.isPresent() && respuesta.get() == btnSi) {
                        crearNuevoCliente();
                    }
                    return;
                }

                // Si hay múltiples resultados, mostrar lista para elegir
                if (clientes.size() > 1) {
                    List<String> opciones = clientes.stream()
                            .map(c -> c.getNombre() + " - " + c.getDni())
                            .toList();

                    ChoiceDialog<String> choiceDialog = new ChoiceDialog<>(opciones.get(0), opciones);
                    choiceDialog.setTitle("Seleccionar Cliente");
                    choiceDialog.setHeaderText("Se encontraron múltiples clientes");
                    choiceDialog.setContentText("Elige uno:");

                    Optional<String> seleccion = choiceDialog.showAndWait();
                    if (seleccion.isPresent()) {
                        int index = opciones.indexOf(seleccion.get());
                        cliente = clientes.get(index);
                    } else {
                        return;
                    }
                } else {
                    cliente = clientes.get(0);
                }
            }

            // Asignar el cliente a la venta
            ventaService.asignarCliente(cliente);
            actualizarLabelCliente();

            System.out.println("✓ Cliente asignado: " + cliente.getNombre());
        }
    }

    /**
     * Crea un nuevo cliente con validación en tiempo real
     */
    private void crearNuevoCliente() throws SQLException {
        Dialog<Cliente> dialog = new Dialog<>();
        dialog.setTitle("Nuevo Cliente");
        dialog.setHeaderText("Introduce los datos del nuevo cliente");

        ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, btnCancelar);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField txtDni = new TextField();
        txtDni.setPromptText("Opcional");

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre completo");

        TextField txtTelefono = new TextField();
        txtTelefono.setPromptText("Opcional");

        TextField txtDireccion = new TextField();
        txtDireccion.setPromptText("Opcional");

        TextField txtEmail = new TextField();
        txtEmail.setPromptText("Opcional");

        grid.add(new Label("DNI/NIF:"), 0, 0);
        grid.add(txtDni, 1, 0);
        grid.add(new Label("Nombre:*"), 0, 1);
        grid.add(txtNombre, 1, 1);
        grid.add(new Label("Teléfono:"), 0, 2);
        grid.add(txtTelefono, 1, 2);
        grid.add(new Label("Dirección:"), 0, 3);
        grid.add(txtDireccion, 1, 3);
        grid.add(new Label("Email:"), 0, 4);
        grid.add(txtEmail, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Deshabilitar botón de guardar inicialmente
        Node botonGuardar = dialog.getDialogPane().lookupButton(btnGuardar);
        botonGuardar.setDisable(true);

        // VALIDACIÓN EN TIEMPO REAL - Solo el nombre es obligatorio
        ChangeListener<String> validador = (obs, oldVal, newVal) -> {
            String nombre = txtNombre.getText().trim();
            boolean nombreValido = !nombre.isEmpty();
            botonGuardar.setDisable(!nombreValido);
        };
        txtNombre.textProperty().addListener(validador);

        javafx.application.Platform.runLater(() -> txtNombre.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                String dni = txtDni.getText().trim();
                String nombre = txtNombre.getText().trim();

                if (nombre.isEmpty()) {
                    mostrarAlerta("Datos incompletos", "El nombre es obligatorio");
                    return null;
                }

                try {
                    // Si se proporciona DNI, verificar si ya existe
                    if (!dni.isEmpty()) {
                        Cliente existente = clienteRepository.findByDni(dni);
                        if (existente != null) {
                            mostrarAlerta("DNI duplicado", "Ya existe un cliente con este DNI");
                            return null;
                        }
                    } else {
                        // Si no hay DNI, generar uno automático único
                        dni = "CLI-" + System.currentTimeMillis();
                    }

                    Cliente nuevoCliente = new Cliente(
                            null,
                            dni,
                            nombre,
                            txtTelefono.getText().trim(),
                            txtDireccion.getText().trim(),
                            txtEmail.getText().trim()
                    );

                    clienteRepository.save(nuevoCliente);
                    System.out.println("✓ Cliente creado: " + nuevoCliente.getNombre());

                    return nuevoCliente;

                } catch (SQLException e) {
                    mostrarError("Error al crear cliente", e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        Optional<Cliente> resultado = dialog.showAndWait();
        if (resultado.isPresent()) {
            ventaService.asignarCliente(resultado.get());
            actualizarLabelCliente();
        }
    }

    /**
     * Actualiza el label que muestra el cliente actual
     */
    private void actualizarLabelCliente() {
        if (lblClienteActual != null) {
            Cliente cliente = ventaService.getClienteActual();
            if (cliente.esClientePorDefecto()) {
                lblClienteActual.setText("Cliente: AL CONTADO");
                lblClienteActual.setStyle("-fx-text-fill: #95a5a6;");
            } else {
                lblClienteActual.setText("Cliente: " + cliente.getNombre());
                lblClienteActual.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            }
        }
    }

    /**
     * Actualiza el cliente en la BD si se modificaron sus datos durante la venta
     */
    private void actualizarClienteEnBD() {
        try {
            Cliente clienteActual = ventaService.getClienteActual();

            // Solo actualizar si no es cliente por defecto
            if (!clienteActual.esClientePorDefecto() && clienteActual.getId() != null) {
                Cliente clienteBD = clienteRepository.findByDni(clienteActual.getDni());

                if (clienteBD != null) {
                    // Verificar si hubo cambios
                    boolean cambios = !clienteBD.getNombre().equals(clienteActual.getNombre()) ||
                            !clienteBD.getTelefono().equals(clienteActual.getTelefono()) ||
                            !clienteBD.getDireccion().equals(clienteActual.getDireccion()) ||
                            !clienteBD.getEmail().equals(clienteActual.getEmail());

                    if (cambios) {
                        clienteRepository.update(clienteActual);
                        System.out.println("✓ Cliente actualizado en BD");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error al actualizar cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void actualizarVista() {
        Factura factura = ventaService.finalizarVenta();

        lineasObservables.clear();
        lineasObservables.addAll(factura.getLineas());

        actualizarTotales(factura);

        // Actualizar contador de artículos
        int totalArticulos = factura.getLineas().stream()
                .mapToInt(LineaFactura::getCantidad)
                .sum();

        if (lblCantidadArticulos != null) {
            lblCantidadArticulos.setText(totalArticulos + " artículo" + (totalArticulos != 1 ? "s" : ""));
        }

        tablaTicket.refresh();
    }

    private void actualizarTotales(Factura factura) {
        lblTotalSinIva.setText(String.format("%.2f€", factura.getTotalSinIva()));
        lblIva.setText(String.format("%.2f€", factura.getTotalIva()));
        lblTotal.setText(String.format("%.2f€", factura.getTotalConIva()));
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

    /**
     * Configura los atajos de teclado para acciones rápidas
     */
    private void configurarAtajosTeclado() {
        // Este método se puede implementar más adelante si quieres añadir
        // atajos de teclado como F1, F2, etc.
    }
}