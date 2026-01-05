package org.tpv.service;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.output.PrinterOutputStream;
import org.tpv.config.Configuracion;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

public class ImpresoraService {

    private final Configuracion config;

    public ImpresoraService(Configuracion config) {
        this.config = config;
    }

    /**
     * Obtiene la lista de impresoras disponibles en el sistema
     */
    public String[] obtenerImpresorasDisponibles() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        String[] nombreImpresoras = new String[printServices.length];

        for (int i = 0; i < printServices.length; i++) {
            nombreImpresoras[i] = printServices[i].getName();
        }

        return nombreImpresoras;
    }

    /**
     * Busca una impresora por nombre
     */
    private PrintService buscarImpresora(String nombreImpresora) {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);

        for (PrintService service : printServices) {
            if (service.getName().equalsIgnoreCase(nombreImpresora)) {
                return service;
            }
        }

        return null;
    }

    /**
     * Imprime el ticket de la factura
     * @param factura La factura a imprimir
     * @param nombreImpresora Nombre de la impresora (null = impresora por defecto)
     * @throws Exception Si hay algún error en la impresión
     */
    public void imprimirTicket(Factura factura, String nombreImpresora) throws Exception {

        PrintService printService;

        if (nombreImpresora != null && !nombreImpresora.isEmpty()) {
            printService = buscarImpresora(nombreImpresora);
            if (printService == null) {
                throw new Exception("No se encontró la impresora: " + nombreImpresora);
            }
        } else {
            // Usar impresora por defecto
            printService = PrintServiceLookup.lookupDefaultPrintService();
            if (printService == null) {
                throw new Exception("No hay impresora por defecto configurada");
            }
        }

        System.out.println("✓ Imprimiendo en: " + printService.getName());

        // Crear el stream de impresión
        PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService);
        EscPos escpos = new EscPos(printerOutputStream);

        try {
            generarContenidoTicket(escpos, factura);
            escpos.close();
        } catch (IOException e) {
            throw new Exception("Error al imprimir: " + e.getMessage(), e);
        }
    }

    /**
     * Genera el contenido del ticket usando comandos ESC/POS
     */
    private void generarContenidoTicket(EscPos escpos, Factura factura) throws IOException {

        // Estilos
        Style titulo = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setJustification(EscPosConst.Justification.Center);

        Style subtitulo = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center);

        Style normal = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Left_Default);

        Style centrado = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setJustification(EscPosConst.Justification.Center);

        Style negrita = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(true);

        // ========== ENCABEZADO ==========
        escpos.writeLF(titulo, config.getNombreTienda());
        escpos.writeLF(centrado, config.getDireccion());
        escpos.writeLF(centrado, config.getCodigoPostal() + " - " + config.getCiudad());
        escpos.writeLF(centrado, "Tel: " + config.getTelefono());
        escpos.writeLF(centrado, "CIF: " + config.getCif());

        if (config.getEmail() != null && !config.getEmail().isEmpty()) {
            escpos.writeLF(centrado, config.getEmail());
        }

        escpos.feed(1);
        escpos.writeLF(centrado, linea(32, "="));
        escpos.feed(1);

        // ========== DATOS DE LA FACTURA ==========
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String fechaFormateada = factura.getFecha().format(formatter);

        if (factura.getNumeroFactura() != null) {
            escpos.writeLF(normal, "Factura: " + factura.getNumeroFactura());
        }
        escpos.writeLF(normal, "Fecha: " + fechaFormateada);

        escpos.feed(1);
        escpos.writeLF(centrado, linea(32, "-"));
        escpos.feed(1);

        // ========== LÍNEAS DE PRODUCTOS ==========
        // Encabezado de tabla
        escpos.writeLF(negrita, formatoLinea("CANT", "PRODUCTO", "IMPORTE"));
        escpos.writeLF(normal, linea(32, "-"));

        for (LineaFactura linea : factura.getLineas()) {
            // Línea 1: Cantidad y nombre
            String cantidad = String.valueOf(linea.getCantidad());
            String nombre = linea.getNombreProducto();

            // Truncar nombre si es muy largo (máximo 18 caracteres)
            if (nombre.length() > 18) {
                nombre = nombre.substring(0, 15) + "...";
            }

            escpos.writeLF(normal, formatoCantidadNombre(cantidad, nombre));

            // Línea 2: Precio unitario, descuento (si hay) y subtotal
            BigDecimal precioConDescuento = linea.getPrecioConDescuento();
            String lineaPrecio = String.format("  %.2f€", precioConDescuento);

            if (linea.getDescuento() > 0) {
                lineaPrecio += String.format(" (-%d%%)", linea.getDescuento());
            }

            String subtotal = String.format("%.2f€", linea.getTotalConIva());

            escpos.writeLF(normal, formatoPrecioSubtotal(lineaPrecio, subtotal));

            escpos.feed(1);
        }

        escpos.writeLF(normal, linea(32, "-"));
        escpos.feed(1);

        // ========== TOTALES ==========
        escpos.writeLF(normal, formatoTotal("Base imponible:",
                String.format("%.2f€", factura.getTotalSinIva())));

        escpos.writeLF(normal, formatoTotal("IVA (" + config.getIvaGeneral() + "%):",
                String.format("%.2f€", factura.getTotalIva())));

        escpos.feed(1);
        escpos.writeLF(negrita, formatoTotal("TOTAL:",
                String.format("%.2f€", factura.getTotalConIva())));

        escpos.feed(1);
        escpos.writeLF(centrado, linea(32, "="));
        escpos.feed(1);

        // ========== PIE DE PÁGINA ==========
        escpos.writeLF(centrado, "Artículos: " + factura.getLineas().size());
        escpos.feed(1);
        escpos.writeLF(centrado, "¡Gracias por su compra!");
        escpos.writeLF(centrado, "¡Vuelva pronto!");

        escpos.feed(4); // Espacio para cortar
        escpos.cut(EscPos.CutMode.FULL);
    }

    // ===== MÉTODOS AUXILIARES DE FORMATO =====

    private String linea(int longitud, String caracter) {
        return caracter.repeat(longitud);
    }

    private String formatoLinea(String col1, String col2, String col3) {
        // Formato: "CANT PRODUCTO            IMPORTE"
        // Ancho: 4 + 18 + 10 = 32 caracteres
        return String.format("%-4s %-18s %8s", col1, col2, col3);
    }

    private String formatoCantidadNombre(String cantidad, String nombre) {
        // Formato: "2    Pan de molde"
        return String.format("%-4s %s", cantidad, nombre);
    }

    private String formatoPrecioSubtotal(String precio, String subtotal) {
        // Formato: "  12.50€              25.00€"
        return String.format("%-22s %8s", precio, subtotal);
    }

    private String formatoTotal(String concepto, String importe) {
        // Formato: "Base imponible:           45.50€"
        int espacios = 32 - concepto.length() - importe.length();
        return concepto + " ".repeat(Math.max(1, espacios)) + importe;
    }
}