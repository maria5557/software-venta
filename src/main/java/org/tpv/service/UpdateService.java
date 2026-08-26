package org.tpv.service;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.tpv.config.VersionApp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de actualizaciones automáticas desde GitHub.
 * <p>
 * Flujo general:
 * <ol>
 *   <li>Descarga {@link VersionApp#URL_CHECK_VERSION} (version.json en la
 *       rama principal del repo) y compara la versión ahí indicada con
 *       {@link VersionApp#VERSION_ACTUAL}.</li>
 *   <li>Si hay una versión más nueva, pregunta al usuario si quiere
 *       actualizar.</li>
 *   <li>Si acepta, descarga el jar a un archivo temporal, verifica su
 *       integridad (sha256 si está disponible, si no un tamaño mínimo),
 *       lo deja listo como "tpv-actualizacion.jar" y genera/lanza un
 *       script .bat que, una vez la app se cierra, sustituye el jar en
 *       uso y vuelve a arrancar MiTPV.exe.</li>
 * </ol>
 * Todo el proceso queda registrado en "actualizaciones.log" dentro de la
 * carpeta de instalación, para poder diagnosticar problemas sin consola.
 */
public class UpdateService {

    private static final long TIMEOUT_CONEXION_SEGUNDOS = 10;
    private static final long TIMEOUT_DESCARGA_MINUTOS = 5;
    /** Tamaño mínimo razonable del jar (ajusta si tu jar final es más pequeño). */
    private static final long TAMANO_MINIMO_JAR_BYTES = 1_000_000L;
    private static final String NOMBRE_JAR_TEMPORAL = "tpv-actualizacion.jar.tmp";
    private static final String NOMBRE_JAR_DESCARGADO = "tpv-actualizacion.jar";
    private static final String NOMBRE_JAR_APP = "tpv-tienda-1.0-SNAPSHOT.jar";
    private static final String NOMBRE_EXE = "MiTPV.exe";
    private static final String NOMBRE_LOG = "actualizaciones.log";
    /** Nombre de la tarea programada temporal usada para desligar el reinicio del proceso actual. */
    private static final String NOMBRE_TAREA_PROGRAMADA = "MiTPV_ActualizarAhora";

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(TIMEOUT_CONEXION_SEGUNDOS))
            .build();

    private volatile File installDirCache;
    private volatile boolean installDirResuelto = false;

    /** Comprobación silenciosa al arrancar la app: no molesta si no hay novedades o falla la red. */
    public void comprobarActualizacionesAsync() {
        ejecutarComprobacion(false);
    }

    /** Comprobación manual (p.ej. botón "Buscar actualizaciones"): siempre informa al usuario del resultado. */
    public void buscarActualizacionesManual() {
        ejecutarComprobacion(true);
    }

    private void ejecutarComprobacion(boolean manual) {
        new Thread(() -> {
            File installDir = resolveInstallDir();
            try {
                if (installDir == null) {
                    log(null, "Ejecutando fuera del paquete instalado (modo desarrollo): comprobación de actualizaciones omitida.");
                    if (manual) {
                        Platform.runLater(() -> mostrarInfo("Modo desarrollo",
                                "La comprobación de actualizaciones solo funciona en la versión instalada (MiTPV.exe)."));
                    }
                    return;
                }

                log(installDir, "Comprobando actualizaciones (versión actual: " + VersionApp.VERSION_ACTUAL + ")...");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(VersionApp.URL_CHECK_VERSION))
                        .timeout(Duration.ofSeconds(TIMEOUT_CONEXION_SEGUNDOS))
                        .header("Cache-Control", "no-cache")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new IOException("El servidor de versiones respondió con código " + response.statusCode());
                }

                String json = response.body();
                String versionRemota = extraerCampoString(json, "version");
                String urlDownload = extraerCampoString(json, "urlDownload");
                String novedades = extraerCampoString(json, "novedades");
                String sha256Esperado = extraerCampoString(json, "sha256"); // opcional

                if (versionRemota == null || urlDownload == null) {
                    throw new IOException("version.json no tiene el formato esperado (falta 'version' o 'urlDownload').");
                }

                log(installDir, "Versión remota: " + versionRemota + " | Versión local: " + VersionApp.VERSION_ACTUAL);

                if (compararVersiones(versionRemota, VersionApp.VERSION_ACTUAL) > 0) {
                    String novedadesFinal = (novedades == null || novedades.isBlank()) ? "(sin detalles)" : novedades;
                    File installDirFinal = installDir;
                    Platform.runLater(() -> solicitarActualizacion(versionRemota, novedadesFinal, urlDownload, sha256Esperado, installDirFinal));
                } else {
                    log(installDir, "La aplicación ya está actualizada.");
                    if (manual) {
                        Platform.runLater(() -> mostrarInfo("Sin actualizaciones",
                                "Ya tienes instalada la última versión (" + VersionApp.VERSION_ACTUAL + ")."));
                    }
                }

            } catch (Exception e) {
                log(installDir, "No se pudo comprobar actualizaciones: " + e);
                if (manual) {
                    Platform.runLater(() -> mostrarError("Error al buscar actualizaciones",
                            "No se pudo contactar con el servidor de actualizaciones.\n\nDetalle: " + e.getMessage()));
                }
            }
        }, "TPV-Update-Check").start();
    }

    private void solicitarActualizacion(String versionNueva, String novedades, String urlDownload,
                                        String sha256Esperado, File installDir) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Actualización disponible");
        alert.setHeaderText("¡Nueva versión " + versionNueva + " disponible!");
        alert.setContentText("Novedades:\n" + novedades +
                "\n\n¿Deseas actualizar ahora?\nLa aplicación se cerrará y se reiniciará automáticamente.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            descargarYAplicarActualizacion(urlDownload, sha256Esperado, installDir);
        }
    }

    private void descargarYAplicarActualizacion(String urlDownload, String sha256Esperado, File installDir) {
        new Thread(() -> {
            File temp = new File(installDir, NOMBRE_JAR_TEMPORAL);
            File destino = new File(installDir, NOMBRE_JAR_DESCARGADO);
            try {
                log(installDir, "Descargando actualización desde: " + urlDownload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlDownload))
                        .timeout(Duration.ofMinutes(TIMEOUT_DESCARGA_MINUTOS))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    throw new IOException("El servidor respondió con código " + response.statusCode() + " al descargar el jar.");
                }

                MessageDigest digest = null;
                if (sha256Esperado != null && !sha256Esperado.isBlank()) {
                    digest = MessageDigest.getInstance("SHA-256");
                }

                try (InputStream origen = response.body();
                     InputStream verificado = (digest != null) ? new DigestInputStream(origen, digest) : origen;
                     OutputStream salida = Files.newOutputStream(temp.toPath(),
                             StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    verificado.transferTo(salida);
                }

                if (temp.length() < TAMANO_MINIMO_JAR_BYTES) {
                    throw new IOException("El archivo descargado parece incompleto (" + temp.length() + " bytes).");
                }

                if (digest != null) {
                    String obtenido = bytesAHex(digest.digest());
                    if (!obtenido.equalsIgnoreCase(sha256Esperado.trim())) {
                        throw new IOException("La suma SHA-256 no coincide (descarga corrupta o manipulada).");
                    }
                    log(installDir, "Verificación SHA-256 correcta.");
                } else {
                    log(installDir, "Sin sha256 en version.json: solo se verificó el tamaño del archivo (" + temp.length() + " bytes).");
                }

                Files.move(temp.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log(installDir, "Actualización descargada correctamente: " + destino.getAbsolutePath());

                crearYEjecutarScriptBat(installDir);

                // Pequeño margen para que la tarea programada quede lanzada
                // antes de cerrar esta app (ver crearYEjecutarScriptBat para
                // el detalle de por qué usamos el Programador de tareas).
                Thread.sleep(500);
                Platform.runLater(() -> System.exit(0));

            } catch (Exception e) {
                log(installDir, "ERROR al descargar/aplicar la actualización: " + e);
                borrarSiExiste(temp);
                Platform.runLater(() -> mostrarError("No se pudo instalar la actualización",
                        e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }, "TPV-Update-Download").start();
    }

    private void crearYEjecutarScriptBat(File installDir) throws IOException {
        File scriptBat = new File(installDir, "actualizar.bat");

        // cd /d "%~dp0" garantiza que el script trabaje en la carpeta donde está instalado MiTPV,
        // sea cual sea la carpeta desde la que se lanzó originalmente el .exe.
        //
        // NOTA sobre "ping -n 4" en vez de "timeout": el comando "timeout" de
        // Windows necesita una consola interactiva real; si el script lo lanza
        // un proceso sin consola, "timeout" falla al instante y NO espera.
        // "ping" a localhost sí funciona igual sin consola y nos da una espera
        // fiable (aprox. 3 segundos con "-n 4").
        String contenidoBat = "@echo off\r\n" +
                "setlocal\r\n" +
                "cd /d \"%~dp0\"\r\n" +
                "echo [%date% %time%] Iniciando actualizacion... >> \"" + NOMBRE_LOG + "\"\r\n" +
                "ping -n 4 127.0.0.1 > nul\r\n" +
                "\r\n" +
                "set INTENTOS=0\r\n" +
                ":REINTENTAR\r\n" +
                "set /a INTENTOS+=1\r\n" +
                "copy /y \"" + NOMBRE_JAR_DESCARGADO + "\" \"app\\" + NOMBRE_JAR_APP + "\" > nul 2>&1\r\n" +
                "if errorlevel 1 (\r\n" +
                "    if %INTENTOS% GEQ 15 (\r\n" +
                "        echo [%date% %time%] ERROR: no se pudo copiar el jar tras %INTENTOS% intentos. >> \"" + NOMBRE_LOG + "\"\r\n" +
                "        goto FIN\r\n" +
                "    )\r\n" +
                "    ping -n 2 127.0.0.1 > nul\r\n" +
                "    goto REINTENTAR\r\n" +
                ")\r\n" +
                "\r\n" +
                "del \"" + NOMBRE_JAR_DESCARGADO + "\" > nul 2>&1\r\n" +
                "echo [%date% %time%] Actualizacion aplicada correctamente. >> \"" + NOMBRE_LOG + "\"\r\n" +
                "start \"\" \"" + NOMBRE_EXE + "\"\r\n" +
                "\r\n" +
                ":FIN\r\n" +
                "schtasks /delete /tn \"" + NOMBRE_TAREA_PROGRAMADA + "\" /f > nul 2>&1\r\n" +
                "del \"%~f0\"\r\n";

        Files.writeString(scriptBat.toPath(), contenidoBat);

        // -------------------------------------------------------------------
        // CÓMO lanzamos este script, y POR QUÉ:
        //
        // No lo lanzamos directamente (con "cmd /c ..." o similar) porque
        // entonces el script queda "colgando" de nuestra propia aplicación
        // Java. Si Windows decide cerrar también los procesos que colgaban
        // de la app cuando esta se cierra (algo que ocurre en ciertas
        // circunstancias con apps empaquetadas), el script muere a medias y
        // el programa nunca se reabre solo.
        //
        // En vez de eso, le pedimos al Programador de tareas de Windows
        // (el mismo sistema que usa, por ejemplo, el propio Windows Update)
        // que ejecute el script por nosotros. Es un servicio del sistema
        // operativo totalmente aparte de nuestra app: en cuanto le hemos
        // encargado la tarea, ya no depende en absoluto de que nuestro
        // programa siga vivo o no.
        //
        // Los 3 pasos son:
        //   1) Borrar cualquier tarea de una actualización anterior que
        //      hubiera quedado a medias (por si acaso).
        //   2) Crear la tarea nueva, indicándole que ejecute nuestro script.
        //      El Programador de tareas exige una hora de inicio, así que le
        //      damos "dentro de 1 minuto" aunque no vayamos a esperar tanto.
        //   3) Decirle "ejecuta esa tarea AHORA MISMO" (sin esperar a la
        //      hora programada). El propio script, al terminar, se borra a
        //      sí mismo y borra la tarea programada, para no dejar basura.
        // -------------------------------------------------------------------

        ejecutarComando(installDir, List.of(
                "schtasks", "/delete", "/tn", NOMBRE_TAREA_PROGRAMADA, "/f"));

        String horaProgramada = LocalTime.now().plusMinutes(1)
                .format(DateTimeFormatter.ofPattern("HH:mm"));

        int codigoCrear = ejecutarComando(installDir, List.of(
                "schtasks", "/create",
                "/tn", NOMBRE_TAREA_PROGRAMADA,
                "/tr", "\"" + scriptBat.getAbsolutePath() + "\"",
                "/sc", "once",
                "/st", horaProgramada,
                "/f"));

        if (codigoCrear != 0) {
            throw new IOException("No se pudo programar el reinicio automático (schtasks /create ha fallado). " +
                    "Revisa actualizaciones.log para más detalles.");
        }

        ejecutarComando(installDir, List.of(
                "schtasks", "/run", "/tn", NOMBRE_TAREA_PROGRAMADA));
    }

    /**
     * Ejecuta un comando externo (usado para schtasks) y registra en el log
     * tanto el comando como su resultado, para poder diagnosticar fallos
     * sin necesidad de consola.
     */
    private int ejecutarComando(File installDir, List<String> comando) {
        try {
            Process proceso = new ProcessBuilder(comando)
                    .redirectErrorStream(true)
                    .start();
            String salida = new String(proceso.getInputStream().readAllBytes());
            boolean terminoATiempo = proceso.waitFor(15, TimeUnit.SECONDS);
            int codigo = terminoATiempo ? proceso.exitValue() : -1;
            log(installDir, "Comando: " + String.join(" ", comando) + " -> código " + codigo +
                    (salida.isBlank() ? "" : " | salida: " + salida.trim().replace("\r\n", " / ")));
            return codigo;
        } catch (Exception e) {
            log(installDir, "No se pudo ejecutar el comando [" + String.join(" ", comando) + "]: " + e);
            return -1;
        }
    }

    /**
     * Determina la carpeta raíz de instalación (donde vive MiTPV.exe) a partir
     * de la ubicación real del jar en ejecución, en vez de confiar en
     * "user.dir" (que puede ser cualquier cosa según cómo se lance el exe).
     * Devuelve null si se está ejecutando desde clases sueltas (IDE / mvn),
     * lo que se interpreta como "modo desarrollo".
     */
    private File resolveInstallDir() {
        if (installDirResuelto) {
            return installDirCache;
        }
        synchronized (this) {
            if (installDirResuelto) {
                return installDirCache;
            }
            installDirResuelto = true;
            try {
                File origen = new File(UpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (origen.isDirectory()) {
                    // Ejecutando desde clases sueltas (IDE o "mvn javafx:run"): no hay nada que actualizar.
                    installDirCache = null;
                    return null;
                }
                // origen suele ser .../MiTPV/app/tpv-tienda-1.0-SNAPSHOT.jar
                File carpetaApp = origen.getParentFile();
                if (carpetaApp != null && carpetaApp.getParentFile() != null && "app".equalsIgnoreCase(carpetaApp.getName())) {
                    installDirCache = carpetaApp.getParentFile();
                } else {
                    installDirCache = carpetaApp;
                }
                return installDirCache;
            } catch (Exception e) {
                installDirCache = null;
                return null;
            }
        }
    }

    private void borrarSiExiste(File f) {
        try {
            Files.deleteIfExists(f.toPath());
        } catch (IOException ignored) {
            // no crítico
        }
    }

    private void log(File installDir, String mensaje) {
        String linea = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + mensaje;
        System.out.println(linea);
        if (installDir != null) {
            try {
                Files.writeString(new File(installDir, NOMBRE_LOG).toPath(), linea + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // si no se puede escribir el log no es crítico
            }
        }
    }

    private void mostrarInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
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

    // ---------------------------------------------------------------------
    // Comparación de versiones tipo semver (1.9 < 1.10 < 2.0), a diferencia
    // de una comparación alfabética de String.compareTo().
    // ---------------------------------------------------------------------

    static int compararVersiones(String v1, String v2) {
        String[] p1 = normalizarVersion(v1).split("\\.");
        String[] p2 = normalizarVersion(v2).split("\\.");
        int max = Math.max(p1.length, p2.length);
        for (int i = 0; i < max; i++) {
            int n1 = i < p1.length ? parseNumero(p1[i]) : 0;
            int n2 = i < p2.length ? parseNumero(p2[i]) : 0;
            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }
        return 0;
    }

    private static String normalizarVersion(String v) {
        if (v == null) return "0";
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        int guion = v.indexOf('-'); // recorta sufijos tipo "-SNAPSHOT"
        if (guion >= 0) {
            v = v.substring(0, guion);
        }
        return v.isEmpty() ? "0" : v;
    }

    private static int parseNumero(String s) {
        try {
            String limpio = s.replaceAll("[^0-9]", "");
            return limpio.isEmpty() ? 0 : Integer.parseInt(limpio);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String bytesAHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // Extractor de campos string de un JSON plano (version.json). Soporta
    // comillas escapadas, saltos de línea y unicode dentro del valor, a
    // diferencia del indexOf() original que se rompía con esos casos.
    // ---------------------------------------------------------------------

    static String extraerCampoString(String json, String campo) {
        if (json == null) return null;
        String clave = "\"" + campo + "\"";
        int idxClave = json.indexOf(clave);
        if (idxClave < 0) return null;

        int idxDosPuntos = json.indexOf(':', idxClave + clave.length());
        if (idxDosPuntos < 0) return null;

        int i = idxDosPuntos + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null; // el valor no es un string (o es null)
        }
        i++; // saltar comilla de apertura

        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char siguiente = json.charAt(i + 1);
                switch (siguiente) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'u':
                        if (i + 5 < json.length()) {
                            String hex = json.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ignored) {
                                // ignora secuencia unicode malformada
                            }
                            i += 4;
                        }
                        break;
                    default:
                        sb.append(siguiente);
                }
                i += 2;
            } else if (c == '"') {
                break; // fin del string
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}