package org.tpv.ui.controller;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
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

        // Configurar Enter en el campo de búsqueda
        txtBusqueda.setOnAction(event -> buscarProducto());

        System.out.println("✓ ProductosController inicializado");
    }

    private void configurarTabla() {
        // Configurar columnas
        colCodigo.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getCodigoBarra()));

        colNombre.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNombre()));

        colPrecio.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue().getPrecioBase()));

        // Formatear columna de precio
        colPrecio.setCellFactory(col -> new TableCell<Producto, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal precio, boolean empty) {
                super.updateItem(precio, empty);
                if (empty || precio == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f €", precio));
                }
            }
        });

        // Configurar columna de acciones
        colAcciones.setCellFactory(col -> new TableCell<Producto, Void>() {
            private final Button btnEditar = new Button("✏️ Editar");
            private final Button btnEliminar = new Button("🗑️ Eliminar");
            private final HBox acciones = new HBox(5, btnEditar, btnEliminar);

            {
                btnEditar.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand;");
                btnEliminar.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand;");

                btnEditar.setOnAction(event -> {
                    Producto producto = getTableView().getItems().get(getIndex());
                    editarProducto(producto);
                });

                btnEliminar.setOnAction(event -> {
                    Producto producto = getTableView().getItems().get(getIndex());
                    eliminarProducto(producto);
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

        if (busqueda.isEmpty()) {
            cargarProductos();
            return;
        }

        try {
            productosObservables.clear();

            // Buscar por código
            Producto productoPorCodigo = productoService.buscarPorCodigo(busqueda);
            if (productoPorCodigo != null) {
                productosObservables.add(productoPorCodigo);
            }

            // Buscar por nombre
            List<Producto> productosPorNombre = productoService.buscarPorNombre(busqueda);
            for (Producto p : productosPorNombre) {
                if (!productosObservables.contains(p)) {
                    productosObservables.add(p);
                }
            }

            lblTotalProductos.setText(productosObservables.size() + " producto(s)");

            if (productosObservables.isEmpty()) {
                mostrarAlerta("No encontrado", "No se encontraron productos con esa búsqueda");
            }

        } catch (SQLException e) {
            mostrarError("Error", "Error al buscar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void crearNuevoProducto() {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.setTitle("Nuevo Producto");
        dialog.setHeaderText("Introduce los datos del nuevo producto");

        ButtonType btnCrear = new ButtonType("Crear", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCrear, btnCancelar);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField txtCodigo = new TextField();
        txtCodigo.setPromptText("Opcional");

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

                    if (codigo.isEmpty()) {
                        codigo = "PROD-" + System.currentTimeMillis();
                    }

                    BigDecimal precio = new BigDecimal(precioStr);

                    Producto nuevoProducto = productoService.crearProducto(codigo, nombre, precio);
                    System.out.println("✓ Producto creado: " + nuevoProducto.getNombre());

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
        if (resultado.isPresent()) {
            cargarProductos();
        }
    }

    private void editarProducto(Producto producto) {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.setTitle("Editar Producto");
        dialog.setHeaderText("Modifica los datos del producto");

        ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, btnCancelar);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField txtCodigo = new TextField(producto.getCodigoBarra());
        txtCodigo.setDisable(true); // Código no editable

        TextField txtNombre = new TextField(producto.getNombre());
        TextField txtPrecio = new TextField(producto.getPrecioBase().toString());

        grid.add(new Label("Código:"), 0, 0);
        grid.add(txtCodigo, 1, 0);
        grid.add(new Label("Nombre:*"), 0, 1);
        grid.add(txtNombre, 1, 1);
        grid.add(new Label("Precio:*"), 0, 2);
        grid.add(txtPrecio, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Node botonGuardar = dialog.getDialogPane().lookupButton(btnGuardar);
        botonGuardar.setDisable(true);

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

            botonGuardar.setDisable(!(nombreValido && precioValido));
        };
        txtNombre.textProperty().addListener(validador);
        txtPrecio.textProperty().addListener(validador);

        javafx.application.Platform.runLater(() -> txtNombre.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                try {
                    String nombre = txtNombre.getText().trim();
                    String precioStr = txtPrecio.getText().trim().replace(",", ".");
                    BigDecimal precio = new BigDecimal(precioStr);

                    producto.setNombre(nombre);
                    producto.setPrecioBase(precio);

                    productoService.actualizarProducto(producto);

                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Producto actualizado");
                    info.setHeaderText("✓ Cambios guardados");
                    info.setContentText("El producto se ha actualizado correctamente");
                    info.showAndWait();

                    System.out.println("✓ Producto actualizado: " + producto.getNombre());
                    return producto;

                } catch (SQLException e) {
                    mostrarError("Error al actualizar", e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        Optional<Producto> resultado = dialog.showAndWait();
        if (resultado.isPresent()) {
            cargarProductos();
        }
    }

    private void eliminarProducto(Producto producto) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar Producto");
        confirmacion.setHeaderText("¿Eliminar este producto?");
        confirmacion.setContentText(producto.getNombre() + "\n" + producto.getCodigoBarra() +
                "\n\n⚠️ Esta acción no se puede deshacer");

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
                                "Puede estar asociado a facturas existentes.\n\n" +
                                e.getMessage());
                e.printStackTrace();
            }
        }
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