package org.tpv.config;

/**
 * Información de versión y actualizaciones automáticas de MiTPV.
 * <p>
 * CÓMO PUBLICAR UNA NUEVA VERSIÓN (checklist):
 * <ol>
 *   <li>Haz tus cambios y súbelos a la rama principal de GitHub.</li>
 *   <li>Sube en {@code VERSION_ACTUAL} (abajo) el nuevo número de versión,
 *       por ejemplo de "3.0" a "3.1". Este número es el que se compara
 *       contra el de version.json para decidir si hay actualización.</li>
 *   <li>Genera el jar: {@code mvn clean package}. Verifica que en target/
 *       solo se use el jar "sombreado" (shaded), el que NO empieza por
 *       "original-" (ese es un subproducto del shade-plugin, no lo uses
 *       para empaquetar ni distribuir).</li>
 *   <li>Vuelve a generar el instalador/app-image con jpackage a partir de
 *       ese jar (verifica que MiTPV.cfg solo tenga UNA línea app.classpath).</li>
 *   <li>Edita version.json en el repositorio (rama principal) con:
 *       <pre>
 *       {
 *         "version": "3.1",
 *         "urlDownload": "https://github.com/OWNER/REPO/releases/download/v3.1/tpv-tienda-1.0-SNAPSHOT.jar",
 *         "novedades": "Descripción breve de los cambios.",
 *         "sha256": "(opcional) hash sha256 del jar, ver más abajo"
 *       }
 *       </pre>
 *   </li>
 *   <li>Crea un "Release" en GitHub con tag {@code v3.1} y sube el jar
 *       generado como asset, con esa misma URL de descarga.</li>
 *   <li>(Opcional pero recomendado) Calcula el sha256 del jar para que la
 *       app verifique que la descarga no está corrupta:
 *       En PowerShell: {@code Get-FileHash tpv-tienda-1.0-SNAPSHOT.jar -Algorithm SHA256}</li>
 * </ol>
 * Todos los TPV comprobarán este archivo la próxima vez que se abran (o al
 * pulsar "Buscar actualizaciones" en Configuración).
 */
public class VersionApp {

    /**
     * Versión de la build actual. Debe coincidir con el tag de GitHub
     * ("vX.Y" -> "X.Y") que corresponde a este jar. Súbela manualmente
     * en cada release, ANTES de generar el jar que vas a publicar.
     */
    public static final String VERSION_ACTUAL = "3.2";

    /**
     * URL del archivo version.json que describe la última versión
     * disponible. Debe apuntar siempre a la rama principal del repo.
     */
    public static final String URL_CHECK_VERSION =
            "https://raw.githubusercontent.com/maria5557/software-venta/refs/heads/main/version.json";

    private VersionApp() {
    }
}