package org.tpv.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.tpv.config.Configuracion;
import org.tpv.config.VersionApp;
import org.tpv.repository.ConfiguracionRepository;
import org.tpv.service.UpdateService;

import java.sql.SQLException;

public class ConfiguracionController {

    @FXML private TextField txtNombreTienda;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtCiudad;
    @FXML private TextField txtCodigoPostal;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtCif;
    @FXML private TextField txtNif;
    @FXML private TextField txtEmail;
    @FXML private Spinner<Integer> spinnerIva;
    @FXML private Button btnGuardar;
    @FXML private Button btnCancelar;
    @FXML private Label lblVersionActual;
    @FXML private Button btnBuscarActualizaciones;

    private ConfiguracionRepository configRepository;
    private Configuracion configuracionActual;

    @FXML
    public void initialize() {
        System.out.println("✓ Inicializando ConfiguracionController...");

        configRepository = new ConfiguracionRepository();

        // Configurar spinner de IVA
        SpinnerValueFactory<Integer> valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 21);
        spinnerIva.setValueFactory(valueFactory);
        spinnerIva.setEditable(true);

        cargarConfiguracion();

        if (lblVersionActual != null) {
            lblVersionActual.setText("Versión instalada: " + VersionApp.VERSION_ACTUAL);
        }

        System.out.println("✓ ConfiguracionController inicializado");
    }

    @FXML
    private void buscarActualizaciones() {
        new UpdateService().buscarActualizacionesManual();
    }

    private void cargarConfiguracion() {
        try {
            configuracionActual = configRepository.obtenerConfiguracion();

            // Cargar datos en los campos
            txtNombreTienda.setText(configuracionActual.getNombreTienda());
            txtDireccion.setText(configuracionActual.getDireccion());
            txtCiudad.setText(configuracionActual.getCiudad());
            txtCodigoPostal.setText(configuracionActual.getCodigoPostal());
            txtTelefono.setText(configuracionActual.getTelefono());
            txtCif.setText(configuracionActual.getCif() != null ? configuracionActual.getCif() : "");
            txtNif.setText(configuracionActual.getNif() != null ? configuracionActual.getNif() : "");
            txtEmail.setText(configuracionActual.getEmail() != null ? configuracionActual.getEmail() : "");
            spinnerIva.getValueFactory().setValue(configuracionActual.getIvaGeneral());

            System.out.println("✓ Configuración cargada");

        } catch (SQLException e) {
            mostrarError("Error", "No se pudo cargar la configuración: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void guardarConfiguracion() {
        try {
            // Validar campos obligatorios
            if (txtNombreTienda.getText().trim().isEmpty()) {
                mostrarAlerta("Datos incompletos", "El nombre de la tienda es obligatorio");
                return;
            }

            // Actualizar objeto de configuración
            configuracionActual.setNombreTienda(txtNombreTienda.getText().trim());
            configuracionActual.setDireccion(txtDireccion.getText().trim());
            configuracionActual.setCiudad(txtCiudad.getText().trim());
            configuracionActual.setCodigoPostal(txtCodigoPostal.getText().trim());
            configuracionActual.setTelefono(txtTelefono.getText().trim());
            configuracionActual.setCif(txtCif.getText().trim());
            configuracionActual.setNif(txtNif.getText().trim());
            configuracionActual.setEmail(txtEmail.getText().trim());
            configuracionActual.setIvaGeneral(spinnerIva.getValue());

            // Guardar en base de datos
            configRepository.actualizarConfiguracion(configuracionActual);

            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Configuración guardada");
            info.setHeaderText("✓ Cambios guardados correctamente");
            info.setContentText("La configuración se ha actualizado en la base de datos.");
            info.showAndWait();

            System.out.println("✓ Configuración guardada exitosamente");

        } catch (SQLException e) {
            mostrarError("Error al guardar", "No se pudo guardar la configuración: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void cancelar() {
        // Recargar la configuración original
        cargarConfiguracion();

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Cambios cancelados");
        info.setHeaderText("Cambios descartados");
        info.setContentText("Se ha restaurado la configuración original.");
        info.showAndWait();
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