package org.tpv.service;

import org.tpv.config.Configuracion;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;

import javax.print.*;
import java.awt.*;
import java.awt.print.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ImpresoraService {

    private final Configuracion config;
    private Factura facturaActual;

    public ImpresoraService(Configuracion config) {
        this.config = config;
    }

    public String[] obtenerImpresorasDisponibles() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        String[] nombreImpresoras = new String[printServices.length];

        System.out.println("=== IMPRESORAS DETECTADAS ===");
        for (int i = 0; i < printServices.length; i++) {
            nombreImpresoras[i] = printServices[i].getName();
            System.out.println((i + 1) + ". " + nombreImpresoras[i]);
        }
        System.out.println("============================");

        return nombreImpresoras;
    }

    private PrintService buscarImpresora(String nombreImpresora) {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);

        for (PrintService service : printServices) {
            if (service.getName().equalsIgnoreCase(nombreImpresora)) {
                return service;
            }
        }

        return null;
    }

    public void imprimirTicket(Factura factura, String nombreImpresora) throws Exception {

        this.facturaActual = factura;

        PrintService printService;

        if (nombreImpresora != null && !nombreImpresora.isEmpty()) {
            printService = buscarImpresora(nombreImpresora);
            if (printService == null) {
                throw new Exception("No se encontró la impresora: " + nombreImpresora);
            }
        } else {
            printService = PrintServiceLookup.lookupDefaultPrintService();
            if (printService == null) {
                throw new Exception("No hay impresora por defecto configurada");
            }
        }

        System.out.println("✓ Usando impresora: " + printService.getName());

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(printService);

        PageFormat pageFormat = job.defaultPage();
        Paper paper = pageFormat.getPaper();

        double width = 226;
        double height = 800;

        paper.setSize(width, height);
        paper.setImageableArea(5, 5, width - 10, height - 10);
        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);

        job.setPrintable(new TicketPrintable(), pageFormat);

        System.out.println("✓ Enviando a impresora...");

        try {
            job.print();
            System.out.println("✓ Ticket enviado correctamente");
            Thread.sleep(500);
        } catch (PrinterException e) {
            throw new Exception("Error al imprimir: " + e.getMessage(), e);
        }
    }

    private class TicketPrintable implements Printable {

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {

            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int x = 10;
            int y = 20;
            int lineHeight = 14;
            int width = (int) pageFormat.getImageableWidth() - 20;

            Font fontNormal = new Font("Monospaced", Font.PLAIN, 9);
            Font fontBold = new Font("Monospaced", Font.BOLD, 9);
            Font fontTitle = new Font("Monospaced", Font.BOLD, 12);
            Font fontSmall = new Font("Monospaced", Font.PLAIN, 8);

            g2d.setColor(Color.BLACK);

            try {
                // ========== ENCABEZADO ==========
                g2d.setFont(fontTitle);
                y = drawCenteredText(g2d, config.getNombreTienda(), x, y, width);
                y += 3;

                g2d.setFont(fontNormal);
                y = drawCenteredText(g2d, config.getDireccion(), x, y, width);
                y = drawCenteredText(g2d, config.getCodigoPostal() + " - " + config.getCiudad(), x, y, width);
                y = drawCenteredText(g2d, "Tel: " + config.getTelefono(), x, y, width);

                if (config.getCif() != null && !config.getCif().isEmpty()) {
                    y = drawCenteredText(g2d, "CIF: " + config.getCif(), x, y, width);
                }

                if (config.getNif() != null && !config.getNif().isEmpty()) {
                    y = drawCenteredText(g2d, "NIF: " + config.getNif(), x, y, width);
                }

                if (config.getEmail() != null && !config.getEmail().isEmpty()) {
                    g2d.setFont(fontSmall);
                    y = drawCenteredText(g2d, config.getEmail(), x, y, width);
                    g2d.setFont(fontNormal);
                }

                y += 5;
                y = drawLine(g2d, "=", x, y, width);
                y += 5;

                // ========== FECHA ==========
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                String fechaFormateada = facturaActual.getFecha().format(formatter);

                if (facturaActual.getNumeroFactura() != null) {
                    g2d.drawString("Factura: " + facturaActual.getNumeroFactura(), x, y);
                    y += lineHeight;
                }

                g2d.drawString("Fecha: " + fechaFormateada, x, y);
                y += lineHeight + 3;

                y = drawLine(g2d, "-", x, y, width);
                y += 5;

                // ========== PRODUCTOS ==========
                g2d.setFont(fontBold);
                g2d.drawString("CANT  PRODUCTO", x, y);
                g2d.drawString("TOTAL", x + width - 45, y);
                y += lineHeight;
                g2d.setFont(fontNormal);
                y = drawLine(g2d, "-", x, y, width);

                for (LineaFactura linea : facturaActual.getLineas()) {
                    y += 3;

                    // Dividir el nombre en múltiples líneas si es necesario
                    String nombreCompleto = linea.getNombreProducto();
                    List<String> lineasNombre = dividirTexto(nombreCompleto, 20);

                    // Primera línea: Cantidad + primera parte del nombre
                    String primeraLinea = String.format("%-4d  %s", linea.getCantidad(), lineasNombre.get(0));
                    g2d.drawString(primeraLinea, x, y);
                    y += lineHeight;

                    // Líneas adicionales del nombre (si las hay)
                    for (int i = 1; i < lineasNombre.size(); i++) {
                        g2d.drawString("      " + lineasNombre.get(i), x, y);
                        y += lineHeight;
                    }

                    // Precio unitario y descuento
                    BigDecimal precioConDescuento = linea.getPrecioConDescuento();
                    String lineaPrecio = String.format("%.2f EUR", precioConDescuento);

                    if (linea.getDescuento() > 0) {
                        lineaPrecio += String.format(" (-%d%%)", linea.getDescuento());
                    }

                    g2d.setFont(fontSmall);
                    g2d.drawString("      " + lineaPrecio, x, y);
                    g2d.setFont(fontNormal);

                    // Subtotal alineado a la derecha
                    String subtotal = String.format("%.2f", linea.getTotalConIva());
                    FontMetrics fm = g2d.getFontMetrics();
                    int subtotalWidth = fm.stringWidth(subtotal);
                    g2d.drawString(subtotal, x + width - subtotalWidth, y);

                    y += lineHeight + 3;
                }

                y = drawLine(g2d, "-", x, y, width);
                y += 5;

                // ========== TOTALES ==========
                y = drawTotalLine(g2d, "Base imponible:", facturaActual.getTotalSinIva(), x, y, width, fontNormal);
                y = drawTotalLine(g2d, "IVA (" + config.getIvaGeneral() + "%):", facturaActual.getTotalIva(), x, y, width, fontNormal);

                y += 3;
                g2d.setFont(fontBold);
                y = drawTotalLine(g2d, "TOTAL:", facturaActual.getTotalConIva(), x, y, width, fontBold);
                y += 3;

                g2d.setFont(fontNormal);
                y = drawLine(g2d, "=", x, y, width);
                y += lineHeight;

                // ========== PIE ==========
                y = drawCenteredText(g2d, "Articulos: " + facturaActual.getLineas().size(), x, y, width);
                y += lineHeight;
                y = drawCenteredText(g2d, "¡Gracias por su compra!", x, y, width);
                y = drawCenteredText(g2d, "Vuelva pronto", x, y, width);

                System.out.println("✓ Ticket generado correctamente");

            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
                e.printStackTrace();
                throw new PrinterException("Error al generar ticket: " + e.getMessage());
            }

            return PAGE_EXISTS;
        }

        private List<String> dividirTexto(String texto, int maxCaracteres) {
            List<String> lineas = new ArrayList<>();

            if (texto.length() <= maxCaracteres) {
                lineas.add(texto);
                return lineas;
            }

            String[] palabras = texto.split(" ");
            StringBuilder lineaActual = new StringBuilder();

            for (String palabra : palabras) {
                if (lineaActual.length() + palabra.length() + 1 <= maxCaracteres) {
                    if (lineaActual.length() > 0) {
                        lineaActual.append(" ");
                    }
                    lineaActual.append(palabra);
                } else {
                    if (lineaActual.length() > 0) {
                        lineas.add(lineaActual.toString());
                        lineaActual = new StringBuilder(palabra);
                    } else {
                        lineas.add(palabra.substring(0, maxCaracteres));
                        lineaActual = new StringBuilder(palabra.substring(maxCaracteres));
                    }
                }
            }

            if (lineaActual.length() > 0) {
                lineas.add(lineaActual.toString());
            }

            return lineas;
        }

        private int drawCenteredText(Graphics2D g2d, String text, int x, int y, int width) {
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int xCentered = x + (width - textWidth) / 2;
            g2d.drawString(text, Math.max(x, xCentered), y);
            return y + 14;
        }

        private int drawLine(Graphics2D g2d, String caracter, int x, int y, int width) {
            StringBuilder linea = new StringBuilder();
            int numCaracteres = width / 6;
            for (int i = 0; i < numCaracteres; i++) {
                linea.append(caracter);
            }
            g2d.drawString(linea.toString(), x, y);
            return y + 14;
        }

        private int drawTotalLine(Graphics2D g2d, String concepto, BigDecimal importe, int x, int y, int width, Font font) {
            g2d.setFont(font);
            g2d.drawString(concepto, x, y);

            String importeStr = String.format("%.2f EUR", importe);
            FontMetrics fm = g2d.getFontMetrics();
            int importeWidth = fm.stringWidth(importeStr);
            g2d.drawString(importeStr, x + width - importeWidth, y);

            return y + 14;
        }
    }
}