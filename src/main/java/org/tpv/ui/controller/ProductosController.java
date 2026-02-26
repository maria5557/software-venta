package org.tpv.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.tpv.domain.Producto;
import org.tpv.service.ProductoService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ProductosController {

    @FXML private TextField txtBusqueda;
    @FXML private Button btnBuscar;
    @FXML private Button btnNuevoProducto;

    @FXML private TableView<Producto> tablaProductos;
    @FXML private TableColumn<Producto, String> colCodigo;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, BigDecimal> colPrecio;
    @FXML private TableColumn<Producto, Void> colAcciones;

    @FXML private Label lblTotalProductos;

    private ProductoService productoService;
    private ObservableList<Producto> productosObservables = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        System.out.println("✓ Inicializando ProductosController...");

        productoService = new ProductoService();

        configurarTabla();
        cargarProductos();

        txtBusqueda.setOnAction(event -> buscarProducto());

        System.out.println("✓ ProductosController inicializado");
    }

    private void configurarTabla() {
        colCodigo.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getCodigoBarra()));

        colNombre.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNombre()));

        colPrecio.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue().getPrecioBase()));

        colPrecio.setCellFactory(col -> new TableCell<Producto, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal precio, boolean empty) {
                super.updateItem(precio, empty);
                if (empty || precio == null) setText(null);
                else setText(String.format("%.2f €", precio));
            }
        });

        colAcciones.setCellFactory(col -> new TableCell<Producto, Void>() {
            private final Button btnEditar   = new Button("Editar");
            private final Button btnEliminar = new Button("Eliminar");
            private final HBox acciones      = new HBox(5, btnEditar, btnEliminar);

            {
                btnEditar.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand;");
                btnEliminar.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand;");
                btnEditar.setOnAction(event   -> editarProducto(getTableView().getItems().get(getIndex())));
                btnEliminar.setOnAction(event -> eliminarProducto(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : acciones);
            }
        });

        tablaProductos.setItems(productosObservables);
    }

    private void cargarProductos() {
        try {
            productosObservables.clear();
            List<Producto> listaProductos = productoService.obtenerTodos();
            productosObservables.addAll(listaProductos);
            lblTotalProductos.setText(productosObservables.size() + " productos");
            System.out.println("✓ Productos cargados: " + productosObservables.size());
        } catch (SQLException e) {
            mostrarError("Error", "No se pudieron cargar los productos");
            e.printStackTrace();
        }
    }

    @FXML
    private void buscarProducto() {
        String busqueda = txtBusqueda.getText().trim();
        if (busqueda.isEmpty()) { cargarProductos(); return; }
        try {
            productosObservables.clear();
            Producto productoPorCodigo = productoService.buscarPorCodigo(busqueda);
            if (productoPorCodigo != null) productosObservables.add(productoPorCodigo);
            List<Producto> productosPorNombre = productoService.buscarPorNombre(busqueda);
            for (Producto p : productosPorNombre)
                if (!productosObservables.contains(p)) productosObservables.add(p);
            lblTotalProductos.setText(productosObservables.size() + " producto(s)");
            if (productosObservables.isEmpty())
                mostrarAlerta("No encontrado", "No se encontraron productos con esa búsqueda");
        } catch (SQLException e) {
            mostrarError("Error", "Error al buscar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== ESTILOS COMPARTIDOS =====
    private static final String ESTILO_LABEL     = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #6c757d; -fx-padding: 0 0 3 2;";
    private static final String ESTILO_INPUT     = "-fx-font-size: 15px; -fx-padding: 10 12; -fx-background-color: white; -fx-border-color: #ced4da; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 42px;";
    private static final String ESTILO_INPUT_FOCO= ESTILO_INPUT + "-fx-border-color: #3498db; -fx-effect: dropshadow(gaussian, rgba(52,152,219,0.25), 6, 0, 0, 0);";
    private static final String ESTILO_INPUT_OPC = "-fx-font-size: 15px; -fx-padding: 10 12; -fx-background-color: white; -fx-border-color: #e9ecef; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 42px;";
    private static final String ESTILO_DESACT    = "-fx-font-size: 15px; -fx-padding: 10 12; -fx-background-color: #e9ecef; -fx-border-color: #dee2e6; -fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-height: 42px; -fx-text-fill: #6c757d;";
    private static final String ESTILO_EURO      = "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #495057; -fx-padding: 10 12; -fx-background-color: #e9ecef; -fx-border-color: #ced4da; -fx-border-radius: 0 6 6 0; -fx-background-radius: 0 6 6 0; -fx-pref-height: 42px;";

    private javafx.scene.layout.VBox crearCabecera(String titulo, String subtitulo) {
        javafx.scene.layout.VBox cab = new javafx.scene.layout.VBox(4);
        cab.setStyle("-fx-background-color: #2c3e50; -fx-padding: 20 24 18 24;");
        Label t = new Label(titulo);
        t.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label s = new Label(subtitulo);
        s.setStyle("-fx-font-size: 12px; -fx-text-fill: #95a5a6;");
        cab.getChildren().addAll(t, s);
        return cab;
    }

    private HBox crearFilaPrecio(TextField txtPrecio) {
        txtPrecio.setStyle(ESTILO_INPUT + "-fx-border-radius: 6 0 0 6; -fx-background-radius: 6 0 0 6;");
        txtPrecio.setMaxWidth(Double.MAX_VALUE);
        txtPrecio.focusedProperty().addListener((o, ov, nv) ->
                txtPrecio.setStyle((nv ? ESTILO_INPUT_FOCO : ESTILO_INPUT) + "-fx-border-radius: 6 0 0 6; -fx-background-radius: 6 0 0 6;"));
        Label euro = new Label("€");
        euro.setStyle(ESTILO_EURO);
        HBox fila = new HBox(0, txtPrecio, euro);
        HBox.setHgrow(txtPrecio, javafx.scene.layout.Priority.ALWAYS);
        return fila;
    }

    private HBox headerConBadge(String texto, String badge, String badgeColor) {
        Label lbl = new Label(texto); lbl.setStyle(ESTILO_LABEL);
        Label b   = new Label(badge);
        b.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: " + badgeColor + "; -fx-background-radius: 3; -fx-padding: 1 5;");
        HBox h = new HBox(8, lbl, b);
        h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return h;
    }

    private HBox headerConAsterisco(String texto) {
        Label lbl = new Label(texto); lbl.setStyle(ESTILO_LABEL);
        Label ast = new Label("*"); ast.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 13px;");
        HBox h = new HBox(3, lbl, ast);
        h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return h;
    }

    // ===== DIÁLOGO: CREAR NUEVO PRODUCTO =====
    @FXML
    private void crearNuevoProducto() {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.setTitle("Nuevo Producto");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setMinWidth(480);

        ButtonType btnCrear    = new ButtonType("✔  Crear producto", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar",           ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCrear, btnCancelar);

        // Campos
        TextField txtCodigo = new TextField();
        txtCodigo.setPromptText("Se generará automáticamente si se deja vacío");
        txtCodigo.setStyle(ESTILO_INPUT_OPC);
        txtCodigo.setMaxWidth(Double.MAX_VALUE);
        txtCodigo.focusedProperty().addListener((o, ov, nv) ->
                txtCodigo.setStyle(nv ? ESTILO_INPUT_FOCO : ESTILO_INPUT_OPC));

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Ej: Café con leche, Barra de pan...");
        txtNombre.setStyle(ESTILO_INPUT);
        txtNombre.setMaxWidth(Double.MAX_VALUE);
        txtNombre.focusedProperty().addListener((o, ov, nv) ->
                txtNombre.setStyle(nv ? ESTILO_INPUT_FOCO : ESTILO_INPUT));

        TextField txtPrecio = new TextField();
        txtPrecio.setPromptText("0.00");
        HBox filaPrecio = crearFilaPrecio(txtPrecio);

        Label lblAviso = new Label("* Campos obligatorios  ·  ℹ El precio debe incluir el IVA");
        lblAviso.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        javafx.scene.layout.VBox cuerpo = new javafx.scene.layout.VBox(16,
                new javafx.scene.layout.VBox(4, headerConBadge("CÓDIGO DE BARRAS", "OPCIONAL", "#95a5a6"), txtCodigo),
                new javafx.scene.layout.VBox(4, headerConAsterisco("NOMBRE DEL PRODUCTO"), txtNombre),
                new javafx.scene.layout.VBox(4, headerConAsterisco("PRECIO CON IVA INCLUIDO"), filaPrecio),
                new Separator(),
                lblAviso
        );
        cuerpo.setStyle("-fx-padding: 24 24 8 24; -fx-background-color: #f8f9fa;");

        dialog.getDialogPane().setContent(new javafx.scene.layout.VBox(
                crearCabecera("Nuevo Producto", "Rellena los datos del producto a añadir"), cuerpo));
        dialog.getDialogPane().setStyle("-fx-padding: 0; -fx-background-color: #f8f9fa;");

        Node botonCrear = dialog.getDialogPane().lookupButton(btnCrear);
        botonCrear.setDisable(true);
        botonCrear.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 5; -fx-cursor: hand;");

        // Validación: solo nombre y precio son obligatorios
        ChangeListener<String> validador = (obs, oldVal, newVal) -> {
            try {
                botonCrear.setDisable(
                        txtNombre.getText().trim().isEmpty()
                                || new BigDecimal(txtPrecio.getText().trim().replace(",", ".")).compareTo(BigDecimal.ZERO) <= 0
                );
            } catch (Exception e) { botonCrear.setDisable(true); }
        };
        txtNombre.textProperty().addListener(validador);
        txtPrecio.textProperty().addListener(validador);
        javafx.application.Platform.runLater(txtNombre::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton != btnCrear) return null;
            try {
                String codigo = txtCodigo.getText().trim();
                if (codigo.isEmpty()) codigo = "PROD-" + System.currentTimeMillis();
                Producto nuevo = productoService.crearProducto(
                        codigo,
                        txtNombre.getText().trim(),
                        new BigDecimal(txtPrecio.getText().trim().replace(",", "."))
                );
                System.out.println("✓ Producto creado: " + nuevo.getNombre());
                return nuevo;
            } catch (SQLException e) { mostrarError("Error al crear producto", e.getMessage()); return null; }
        });

        if (dialog.showAndWait().isPresent()) cargarProductos();
    }

    // ===== DIÁLOGO: EDITAR PRODUCTO =====
    private void editarProducto(Producto producto) {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.setTitle("Editar Producto");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setMinWidth(480);

        ButtonType btnGuardar  = new ButtonType("✔  Guardar cambios", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar",             ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, btnCancelar);

        // Campo código (solo lectura)
        TextField txtCodigo = new TextField(producto.getCodigoBarra());
        txtCodigo.setDisable(true);
        txtCodigo.setStyle(ESTILO_DESACT);
        txtCodigo.setMaxWidth(Double.MAX_VALUE);

        // Campo nombre
        TextField txtNombre = new TextField(producto.getNombre());
        txtNombre.setStyle(ESTILO_INPUT);
        txtNombre.setMaxWidth(Double.MAX_VALUE);
        txtNombre.focusedProperty().addListener((o, ov, nv) ->
                txtNombre.setStyle(nv ? ESTILO_INPUT_FOCO : ESTILO_INPUT));

        // Campo precio
        TextField txtPrecio = new TextField(producto.getPrecioBase().toString());
        HBox filaPrecio = crearFilaPrecio(txtPrecio);

        Label lblBloqueo = new Label("🔒 El código de barras no es editable");
        lblBloqueo.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        javafx.scene.layout.VBox cuerpo = new javafx.scene.layout.VBox(16,
                new javafx.scene.layout.VBox(4,
                        new Label("CÓDIGO DE BARRAS") {{ setStyle(ESTILO_LABEL); }},
                        txtCodigo,
                        lblBloqueo),
                new javafx.scene.layout.VBox(4, headerConAsterisco("NOMBRE DEL PRODUCTO"), txtNombre),
                new javafx.scene.layout.VBox(4, headerConAsterisco("PRECIO CON IVA INCLUIDO"), filaPrecio)
        );
        cuerpo.setStyle("-fx-padding: 24 24 16 24; -fx-background-color: #f8f9fa;");

        dialog.getDialogPane().setContent(new javafx.scene.layout.VBox(
                crearCabecera("Editar Producto", "Código: " + producto.getCodigoBarra()), cuerpo));
        dialog.getDialogPane().setStyle("-fx-padding: 0; -fx-background-color: #f8f9fa;");

        Node botonGuardar = dialog.getDialogPane().lookupButton(btnGuardar);
        botonGuardar.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 5; -fx-cursor: hand;");

        // Validación: solo nombre y precio son obligatorios
        ChangeListener<String> validador = (obs, oldVal, newVal) -> {
            try {
                botonGuardar.setDisable(
                        txtNombre.getText().trim().isEmpty()
                                || new BigDecimal(txtPrecio.getText().trim().replace(",", ".")).compareTo(BigDecimal.ZERO) <= 0
                );
            } catch (Exception e) { botonGuardar.setDisable(true); }
        };
        txtNombre.textProperty().addListener(validador);
        txtPrecio.textProperty().addListener(validador);
        javafx.application.Platform.runLater(txtNombre::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton != btnGuardar) return null;
            try {
                producto.setNombre(txtNombre.getText().trim());
                producto.setPrecioBase(new BigDecimal(txtPrecio.getText().trim().replace(",", ".")));
                productoService.actualizarProducto(producto);
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Producto actualizado");
                info.setHeaderText("✓ Cambios guardados");
                info.setContentText("El producto se ha actualizado correctamente");
                info.showAndWait();
                System.out.println("✓ Producto actualizado: " + producto.getNombre());
                return producto;
            } catch (SQLException e) { mostrarError("Error al actualizar", e.getMessage()); return null; }
        });

        if (dialog.showAndWait().isPresent()) cargarProductos();
    }

    // ===== ELIMINAR PRODUCTO =====
    private void eliminarProducto(Producto producto) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar Producto");
        confirmacion.setHeaderText("¿Eliminar este producto?");
        confirmacion.setContentText(producto.getNombre() + "\n" + producto.getCodigoBarra() +
                "\n\nEsta acción no se puede deshacer");

        Optional<ButtonType> resultado = confirmacion.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            try {
                productoService.eliminarProducto(producto.getCodigoBarra());
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Producto eliminado");
                info.setHeaderText("✓ Producto eliminado correctamente");
                info.showAndWait();
                System.out.println("✓ Producto eliminado: " + producto.getNombre());
                cargarProductos();
            } catch (SQLException e) {
                mostrarError("Error al eliminar",
                        "No se pudo eliminar el producto.\n" +
                                "Puede estar asociado a facturas existentes.\n\n" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titulo); alert.setHeaderText(null); alert.setContentText(mensaje); alert.showAndWait();
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo); alert.setHeaderText(null); alert.setContentText(mensaje); alert.showAndWait();
    }
}