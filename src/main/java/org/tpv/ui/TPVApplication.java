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
            // Opcional: agregar productos de prueba
            agregarProductosDePrueba();
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

    private void agregarProductosDePrueba() throws SQLException {
        ProductoService service = new ProductoService();

        // Solo añadir si no existen
        if (service.buscarPorCodigo("001") == null) {
            service.crearProducto("001", "Leche Entera 1L", new BigDecimal("1.20"));
            service.crearProducto("002", "Pan Barra", new BigDecimal("0.80"));
            service.crearProducto("003", "Agua Mineral 1.5L", new BigDecimal("0.50"));
            service.crearProducto("004", "Huevos Docena", new BigDecimal("2.50"));
            service.crearProducto("005", "Queso Manchego 250g", new BigDecimal("4.80"));
            System.out.println("✓ Productos de prueba agregados");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
