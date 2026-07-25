package org.tpv.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.tpv.config.SesionUsuario;
import org.tpv.domain.Empleado;
import org.tpv.repository.EmpleadoRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainController {

    @FXML private StackPane contenidoCentral;
    @FXML private Label lblUsuario;
    @FXML private Label lblFechaHora;

    @FXML private Button btnMenuVentas;
    @FXML private Button btnMenuProductos;
    @FXML private Button btnMenuFacturas;
    @FXML private Button btnMenuClientes;
    @FXML private Button btnMenuConfiguracion;

    @FXML
    public void initialize() throws SQLException {
        System.out.println("✓ Inicializando MainController...");

        // 1. Mostrar nombre del usuario activo en la barra del menú principal
        if (lblUsuario != null && SesionUsuario.getEmpleadoActivo() != null) {
            lblUsuario.setText("Usuario: " + SesionUsuario.getEmpleadoActivo().getNombre());
        }

        // Iniciar reloj
        iniciarReloj();

        // COMPROBAR ACTUALIZACIONES AL ARRANCAR
        try {
            org.tpv.service.UpdateService updateService = new org.tpv.service.UpdateService();
            updateService.comprobarActualizacionesAsync();
        } catch (Exception e) {
            System.err.println("No se pudo iniciar el servicio de actualización: " + e.getMessage());
        }

        // Cargar vista de ventas por defecto
        mostrarVentas();


        System.out.println("✓ MainController inicializado");
    }

    @FXML
    private void mostrarVentas() {
        cargarVista("/org/tpv/ui/view/venta.fxml");
        actualizarEstiloBotonActivo(btnMenuVentas);
    }

    @FXML
    private void mostrarProductos() {
        cargarVista("/org/tpv/ui/view/productos.fxml");
        actualizarEstiloBotonActivo(btnMenuProductos);
    }

    @FXML
    private void mostrarFacturas() {
        cargarVista("/org/tpv/ui/view/facturas.fxml");
        actualizarEstiloBotonActivo(btnMenuFacturas);
    }

    @FXML
    private void mostrarClientes() {
        cargarVista("/org/tpv/ui/view/clientes.fxml");
        actualizarEstiloBotonActivo(btnMenuClientes);
    }

    @FXML
    private void mostrarConfiguracion() {
        cargarVista("/org/tpv/ui/view/configuracion.fxml");
        actualizarEstiloBotonActivo(btnMenuConfiguracion);
    }

    private void cargarVista(String rutaFxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(rutaFxml));
            Parent vista = loader.load();
            contenidoCentral.getChildren().clear();
            contenidoCentral.getChildren().add(vista);
            System.out.println("✓ Vista cargada: " + rutaFxml);
        } catch (IOException e) {
            System.err.println("❌ Error al cargar vista: " + rutaFxml);
            e.printStackTrace();
        }
    }

    private void actualizarEstiloBotonActivo(Button botonActivo) {
        // Resetear todos los botones
        btnMenuVentas.setStyle(getEstiloBotonInactivo());
        btnMenuProductos.setStyle(getEstiloBotonMenuInactivo());
        btnMenuFacturas.setStyle(getEstiloBotonMenuInactivo());
        btnMenuClientes.setStyle(getEstiloBotonMenuInactivo());
        btnMenuConfiguracion.setStyle(getEstiloBotonMenuInactivo());

        // Activar el botón seleccionado
        if (botonActivo == btnMenuVentas) {
            botonActivo.setStyle(getEstiloBotonActivo());
        } else {
            botonActivo.setStyle(getEstiloBotonMenuActivo());
        }
    }

    private String getEstiloBotonActivo() {
        return "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 15; -fx-background-radius: 5; -fx-cursor: hand; -fx-font-weight: bold;";
    }

    private String getEstiloBotonInactivo() {
        return "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 15; -fx-background-radius: 5; -fx-cursor: hand; -fx-font-weight: bold;";
    }

    private String getEstiloBotonMenuActivo() {
        return "-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 12 15; -fx-background-radius: 5; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; -fx-font-weight: bold;";
    }

    private String getEstiloBotonMenuInactivo() {
        return "-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 12 15; -fx-background-radius: 5; -fx-cursor: hand; -fx-alignment: CENTER-LEFT;";
    }

    private void iniciarReloj() {
        Thread reloj = new Thread(() -> {
            while (true) {
                try {
                    javafx.application.Platform.runLater(() -> {
                        if (lblFechaHora != null) {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                            lblFechaHora.setText(LocalDateTime.now().format(formatter));
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
}