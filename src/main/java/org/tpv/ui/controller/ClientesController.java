package org.tpv.ui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.tpv.domain.Cliente;
import org.tpv.repository.ClienteRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ClientesController {

    @FXML private TextField txtBusquedaCliente;
    @FXML private Button btnBuscarCliente;
    @FXML private Button btnNuevoCliente;

    @FXML private TableView<Cliente> tablaClientes;
    @FXML private TableColumn<Cliente, String> colDni;
    @FXML private TableColumn<Cliente, String> colNombre;
    @FXML private TableColumn<Cliente, String> colTelefono;
    @FXML private TableColumn<Cliente, String> colEmail;
    @FXML private TableColumn<Cliente, Void> colAcciones;

    @FXML private Label lblTotalClientes;

    private ClienteRepository clienteRepository;
    private ObservableList<Cliente> clientesObservables = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        System.out.println("✓ Inicializando ClientesController...");

        clienteRepository = new ClienteRepository();

        configurarTabla();
        cargarClientes();

        // Configurar Enter en el campo de búsqueda
        txtBusquedaCliente.setOnAction(event -> buscarCliente());

        System.out.println("✓ ClientesController inicializado");
    }

    private void configurarTabla() {
        // Configurar columnas
        colDni.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDni()));

        colNombre.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getNombre()));

        colTelefono.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getTelefono()));

        colEmail.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getEmail()));

        // Configurar columna de acciones
        colAcciones.setCellFactory(col -> new TableCell<Cliente, Void>() {
            private final Button btnEditar = new Button("✏️ Editar");
            private final Button btnEliminar = new Button("🗑️ Eliminar");
            private final HBox acciones = new HBox(5, btnEditar, btnEliminar);

            {
                btnEditar.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand;");
                btnEliminar.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 5 10; -fx-cursor: hand;");

                btnEditar.setOnAction(event -> {
                    Cliente cliente = getTableView().getItems().get(getIndex());
                    editarCliente(cliente);
                });

                btnEliminar.setOnAction(event -> {
                    Cliente cliente = getTableView().getItems().get(getIndex());
                    eliminarCliente(cliente);
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

        tablaClientes.setItems(clientesObservables);
    }

    private void cargarClientes() {
        try {
            List<Cliente> clientes = clienteRepository.findAll();
            clientesObservables.clear();
            clientesObservables.addAll(clientes);

            lblTotalClientes.setText(clientesObservables.size() + " clientes");

        } catch (SQLException e) {
            mostrarError("Error", "No se pudieron cargar los clientes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void buscarCliente() {
        String busqueda = txtBusquedaCliente.getText().trim();

        if (busqueda.isEmpty()) {
            cargarClientes();
            return;
        }

        try {
            clientesObservables.clear();

            // Buscar por DNI
            Cliente clientePorDni = clienteRepository.findByDni(busqueda);
            if (clientePorDni != null) {
                clientesObservables.add(clientePorDni);
            }

            // Buscar por nombre
            List<Cliente> clientesPorNombre = clienteRepository.findByNombre(busqueda);
            for (Cliente c : clientesPorNombre) {
                if (!clientesObservables.contains(c)) {
                    clientesObservables.add(c);
                }
            }

            lblTotalClientes.setText(clientesObservables.size() + " cliente(s)");

            if (clientesObservables.isEmpty()) {
                mostrarAlerta("No encontrado", "No se encontraron clientes con esa búsqueda");
            }

        } catch (SQLException e) {
            mostrarError("Error", "Error al buscar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void crearNuevoCliente() {
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

        Node botonGuardar = dialog.getDialogPane().lookupButton(btnGuardar);
        botonGuardar.setDisable(true);

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
                    if (!dni.isEmpty()) {
                        Cliente existente = clienteRepository.findByDni(dni);
                        if (existente != null) {
                            mostrarAlerta("DNI duplicado", "Ya existe un cliente con este DNI");
                            return null;
                        }
                    } else {
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
            cargarClientes();
        }
    }

    private void editarCliente(Cliente cliente) {
        Dialog<Cliente> dialog = new Dialog<>();
        dialog.setTitle("Editar Cliente");
        dialog.setHeaderText("Modifica los datos del cliente");

        ButtonType btnGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, btnCancelar);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField txtDni = new TextField(cliente.getDni());
        txtDni.setDisable(true); // DNI no editable

        TextField txtNombre = new TextField(cliente.getNombre());
        TextField txtTelefono = new TextField(cliente.getTelefono());
        TextField txtDireccion = new TextField(cliente.getDireccion());
        TextField txtEmail = new TextField(cliente.getEmail());

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

        Node botonGuardar = dialog.getDialogPane().lookupButton(btnGuardar);
        botonGuardar.setDisable(true);

        ChangeListener<String> validador = (obs, oldVal, newVal) -> {
            String nombre = txtNombre.getText().trim();
            boolean nombreValido = !nombre.isEmpty();
            botonGuardar.setDisable(!nombreValido);
        };
        txtNombre.textProperty().addListener(validador);

        javafx.application.Platform.runLater(() -> txtNombre.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnGuardar) {
                try {
                    String nombre = txtNombre.getText().trim();

                    if (nombre.isEmpty()) {
                        mostrarAlerta("Datos incompletos", "El nombre es obligatorio");
                        return null;
                    }

                    cliente.setNombre(nombre);
                    cliente.setTelefono(txtTelefono.getText().trim());
                    cliente.setDireccion(txtDireccion.getText().trim());
                    cliente.setEmail(txtEmail.getText().trim());

                    clienteRepository.update(cliente);

                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Cliente actualizado");
                    info.setHeaderText("✓ Cambios guardados");
                    info.setContentText("El cliente se ha actualizado correctamente");
                    info.showAndWait();

                    System.out.println("✓ Cliente actualizado: " + cliente.getNombre());
                    return cliente;

                } catch (SQLException e) {
                    mostrarError("Error al actualizar", e.getMessage());
                    e.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        Optional<Cliente> resultado = dialog.showAndWait();
        if (resultado.isPresent()) {
            cargarClientes();
        }
    }

    private void eliminarCliente(Cliente cliente) {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar Cliente");
        confirmacion.setHeaderText("¿Eliminar este cliente?");
        confirmacion.setContentText(cliente.getNombre() + "\nDNI: " + cliente.getDni() +
                "\n\n⚠️ Esta acción no se puede deshacer");

        Optional<ButtonType> resultado = confirmacion.showAndWait();
        if (resultado.isPresent() && resultado.get() == ButtonType.OK) {
            try {
                clienteRepository.delete(cliente.getDni());

                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Cliente eliminado");
                info.setHeaderText("✓ Cliente eliminado correctamente");
                info.showAndWait();

                System.out.println("✓ Cliente eliminado: " + cliente.getNombre());
                cargarClientes();

            } catch (SQLException e) {
                mostrarError("Error al eliminar",
                        "No se pudo eliminar el cliente.\n" +
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