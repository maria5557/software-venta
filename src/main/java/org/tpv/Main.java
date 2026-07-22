package org.tpv;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.tpv.config.SesionUsuario;
import org.tpv.database.DatabaseManager;
import org.tpv.domain.Empleado;
import org.tpv.repository.EmpleadoRepository;

import java.sql.SQLException;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Cargar la vista principal FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/tpv/ui/view/main.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            primaryStage.setTitle("TPV - Punto de Venta");
            primaryStage.setMaximized(true); // Ventana maximizada
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            System.err.println("❌ Error al cargar la interfaz gráfica: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            // 1. Inicializar la base de datos SQLite
            DatabaseManager.inicializarBaseDatos();

            // 2. Cargar el empleado por defecto (ID = 1) e iniciarlo en la sesión global
            EmpleadoRepository empleadoRepo = new EmpleadoRepository();
            Empleado empleadoInicial = empleadoRepo.findById(1L);

            if (empleadoInicial != null) {
                SesionUsuario.setEmpleadoActivo(empleadoInicial);
                System.out.println("✓ Sesión iniciada con usuario: " + empleadoInicial.getNombre());
            } else {
                System.out.println("⚠ No se encontró el empleado ID 1 en BD");
            }

        } catch (SQLException e) {
            System.err.println("❌ Error con la base de datos al iniciar: " + e.getMessage());
            e.printStackTrace();
        }

        // 3. Lanzar la aplicación JavaFX
        launch(args);
    }
}