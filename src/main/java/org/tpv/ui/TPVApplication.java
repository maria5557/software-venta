package org.tpv.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.tpv.database.DatabaseManager;
import org.tpv.service.ProductoService;

import java.math.BigDecimal;
import java.sql.SQLException;

public class TPVApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        System.out.println("=== INICIANDO TPV ===");

        // 1. Inicializar base de datos
        try {
            DatabaseManager.inicializarBaseDatos();
        } catch (SQLException e) {
            System.err.println("❌ Error inicializando BD: " + e.getMessage());
            e.printStackTrace();
        }

        // 2. Cargar la vista FXML
        FXMLLoader fxmlLoader = new FXMLLoader(
                TPVApplication.class.getResource("/org/tpv/ui/view/main.fxml")
        );

        Scene scene = new Scene(fxmlLoader.load());

        // 3. Configurar ventana
        stage.setTitle("TPV - Terminal Punto de Venta");
        stage.setScene(scene);
        stage.setMaximized(true); // Pantalla completa
        stage.show();

        System.out.println("✓ TPV iniciado correctamente");
    }


    public static void main(String[] args) {
        launch(args);
    }
}
