package org.tpv.service;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.tpv.config.VersionApp;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.Optional;

public class UpdateService {

    public void comprobarActualizacionesAsync() {
        // Ejecutamos la comprobación en un hilo secundario para no congelar la pantalla
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(VersionApp.URL_CHECK_VERSION))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String json = response.body();

                    // Extracción básica de campos del JSON
                    String versionRemota = extraerValorJson(json, "version");
                    String urlDownload = extraerValorJson(json, "urlDownload");
                    String novedades = extraerValorJson(json, "novedades");

                    if (esNuevaVersion(versionRemota, VersionApp.VERSION_ACTUAL)) {
                        Platform.runLater(() -> solicitarActualizacion(versionRemota, novedades, urlDownload));
                    }
                }
            } catch (Exception e) {
                // Si no hay internet o falla el servidor, la app inicia normalmente sin molestar
                System.out.println("ℹ️ No se pudo comprobar actualizaciones: " + e.getMessage());
            }
        }).start();
    }

    private boolean esNuevaVersion(String versionRemota, String versionActual) {
        if (versionRemota == null || versionActual == null) return false;
        return versionRemota.compareTo(versionActual) > 0;
    }

    private void solicitarActualizacion(String versionNueva, String novedades, String urlDownload) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Actualización disponible");
        alert.setHeaderText("¡Nueva versión " + versionNueva + " disponible!");
        alert.setContentText("Novedades:\n" + novedades + "\n\n¿Deseas actualizar ahora?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            descargarEAplicarActualizacion(urlDownload);
        }
    }

    private void descargarEAplicarActualizacion(String urlDownload) {
        new Thread(() -> {
            try {
                File dirApp = new File(System.getProperty("user.dir"));
                File archivoNuevo = new File(dirApp, "tpv-actualizacion.jar");

                // 1. Descargar archivo .jar nuevo
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlDownload)).GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                Files.copy(response.body(), archivoNuevo.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // 2. Crear y ejecutar el script BAT de reemplazo
                crearYEjecutarScriptBat();

                // 3. Cerrar la aplicación actual para liberar el archivo .jar
                Platform.runLater(() -> {
                    System.exit(0);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert err = new Alert(Alert.AlertType.ERROR, "Error al descargar actualización: " + e.getMessage());
                    err.showAndWait();
                });
            }
        }).start();
    }

    private void crearYEjecutarScriptBat() throws IOException {
        File scriptBat = new File("actualizar.bat");

        // El script espera 3 segundos a que la app Java se cierre del todo,
        // reemplaza el .jar viejo por el nuevo y vuelve a lanzar MiTPV.exe
        String contenidoBat = """
            @echo off
            timeout /t 3 /nobreak > nul
            if exist tpv-actualizacion.jar (
                copy /y tpv-actualizacion.jar app\\tpv-tienda-1.0-SNAPSHOT.jar
                del tpv-actualizacion.jar
            )
            start "" "MiTPV.exe"
            del "%~f0"
            """;

        Files.writeString(scriptBat.toPath(), contenidoBat);

        // Lanza el script BAT en segundo plano
        new ProcessBuilder("cmd", "/c", scriptBat.getAbsolutePath()).start();
    }

    private String extraerValorJson(String json, String clave) {
        try {
            String busqueda = "\"" + clave + "\":";
            int inicio = json.indexOf(busqueda) + busqueda.length();
            int finComilla1 = json.indexOf("\"", inicio) + 1;
            int finComilla2 = json.indexOf("\"", finComilla1);
            return json.substring(finComilla1, finComilla2);
        } catch (Exception e) {
            return "";
        }
    }
}