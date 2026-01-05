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
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;
import org.tpv.domain.Producto;
import org.tpv.service.ProductoService;
import org.tpv.service.VentaService;

import java.math.BigDecimal;
import java.sql.SQLException;
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

    // ===== SERVICIOS Y DATOS =====
    private ProductoService productoService;
    private VentaService ventaService;
    private Configuracion config;

    private ObservableList<LineaFactura> lineasObservables = FXCollections.observableArrayList();

    // ===== INICIALIZACIÓN =====
    @FXML
    public void initialize() {
        System.out.println("✓ Inicializando VentaController...");

        // Inicializar servicios
        productoService = new ProductoService();
        config = new Configuracion(21); // IVA del 21%
        ventaService = new VentaService(config);
        ventaService.iniciarVenta();

        // Configurar las columnas de la tabla
        configurarTabla();

        // Focus en el campo de código
        txtCodigoBarra.requestFocus();

        System.out.println("✓ VentaController inicializado correctamente");
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

        if (codigo.isEmpty()) {
            mostrarAlerta("Campo vacío", "Por favor, introduce un código de barras");
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

        // ⭐ ACTUALIZAR PRODUCTOS EN LA BD CON LOS CAMBIOS ⭐
        actualizarProductosEnBD(factura);

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Cobro realizado");
        info.setHeaderText("✓ Venta finalizada correctamente");
        info.setContentText(String.format(
                """
                ═══════════════════════════════
                Base imponible: %.2f€
                IVA (21%%):      %.2f€
                ───────────────────────────────
                TOTAL:          %.2f€
                ═══════════════════════════════
                
                Artículos: %d
                
                ✓ Productos actualizados en la BD
                """,
                factura.getTotalSinIva(),
                factura.getTotalIva(),
                factura.getTotalConIva(),
                factura.getLineas().size()
        ));

        info.showAndWait();

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

    // ===== MÉTODOS AUXILIARES =====


    private void actualizarVista() {
        Factura factura = ventaService.finalizarVenta();

        lineasObservables.clear();
        lineasObservables.addAll(factura.getLineas());

        actualizarTotales(factura);

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
}