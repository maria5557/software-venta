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

/**
 * Versión DEFINITIVA del servicio de impresión.
 * - Cabecera completa (CIF, NIF, Email).
 * - Cliente con soporte para nombres largos (multilínea).
 * - Lógica de descuentos condicional.
 * - Ajustes de márgenes de seguridad para evitar cortes.
 */
public class ImpresoraService {

    private final Configuracion config;
    private Factura facturaActual;

    private static final double ANCHO_PAPEL_PUNTOS = 138.0;
    private static final int MARGEN_IZQUIERDO = 10;
    private static final int MARGEN_DERECHO = 2;
    private static final int ANCHO_IMPRIMIBLE = (int)(ANCHO_PAPEL_PUNTOS - MARGEN_IZQUIERDO - MARGEN_DERECHO);
    private static final int MAX_CHARS_PER_LINE = 24;

    public ImpresoraService(Configuracion config) {
        this.config = config;
    }

    public String[] obtenerImpresorasDisponibles() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        String[] nombreImpresoras = new String[printServices.length];
        for (int i = 0; i < printServices.length; i++) {
            nombreImpresoras[i] = printServices[i].getName();
        }
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
            if (printService == null) throw new Exception("No se encontró la impresora: " + nombreImpresora);
        } else {
            printService = PrintServiceLookup.lookupDefaultPrintService();
            if (printService == null) throw new Exception("No hay impresora por defecto");
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(printService);

        PageFormat pageFormat = job.defaultPage();
        Paper paper = pageFormat.getPaper();

        double width = ANCHO_PAPEL_PUNTOS;
        double height = 1500;

        paper.setSize(width, height);
        paper.setImageableArea(MARGEN_IZQUIERDO, 0, ANCHO_IMPRIMIBLE, height);
        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);

        job.setPrintable(new TicketPrintable(), pageFormat);

        try {
            job.print();
        } catch (PrinterException e) {
            throw new Exception("Error al imprimir: " + e.getMessage(), e);
        }
    }

    private class TicketPrintable implements Printable {

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            if (pageIndex > 0) return NO_SUCH_PAGE;

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int x = (int) pageFormat.getImageableX();
            int y = 15;
            int lineHeight = 12;
            int width = (int) pageFormat.getImageableWidth();

            Font fontNormal = new Font("Monospaced", Font.PLAIN, 8);
            Font fontBold = new Font("Monospaced", Font.BOLD, 8);
            Font fontTitle = new Font("Monospaced", Font.BOLD, 10);
            Font fontSmall = new Font("Monospaced", Font.PLAIN, 7);

            g2d.setColor(Color.BLACK);

            try {
                // ========== ENCABEZADO COMPLETO ==========
                g2d.setFont(fontTitle);
                y = drawCenteredText(g2d, config.getNombreTienda(), x, y, width);

                g2d.setFont(fontSmall);
                y = drawCenteredText(g2d, config.getDireccion(), x, y, width);
                y = drawCenteredText(g2d, config.getCodigoPostal() + " " + config.getCiudad(), x, y, width);
                y = drawCenteredText(g2d, "Tel: " + config.getTelefono(), x, y, width);

                // ⭐ CIF Y NIF RESTAURADOS ⭐
                if (config.getCif() != null && !config.getCif().isEmpty()) {
                    y = drawCenteredText(g2d, "CIF: " + config.getCif(), x, y, width);
                }
                if (config.getNif() != null && !config.getNif().isEmpty()) {
                    y = drawCenteredText(g2d, "NIF: " + config.getNif(), x, y, width);
                }

                if (config.getEmail() != null && !config.getEmail().isEmpty()) {
                    y = drawCenteredText(g2d, config.getEmail(), x, y, width);
                }

                y += 4;
                y = drawLine(g2d, "=", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // ========== INFO FACTURA ==========
                g2d.setFont(fontBold);
                y = drawCenteredText(g2d, "FACTURA SIMPLIFICADA", x, y, width);
                g2d.setFont(fontNormal);

                if (facturaActual.getNumeroFactura() != null) {
                    g2d.drawString("No: " + facturaActual.getNumeroFactura(), x, y);
                    y += lineHeight;
                }

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                g2d.drawString("Fecha: " + facturaActual.getFechaEmision().format(formatter), x, y);
                y += lineHeight;

                // CLIENTE CON SOPORTE MULTILÍNEA
                String nombreCliente = (facturaActual.getClienteNombre() != null) ? facturaActual.getClienteNombre() : "AL CONTADO";
                List<String> lineasCliente = dividirTexto("Cliente: " + nombreCliente, MAX_CHARS_PER_LINE);
                for (String linea : lineasCliente) {
                    g2d.drawString(linea, x, y);
                    y += lineHeight;
                }

                // EMPLEADO
                String nombreEmpleado = (facturaActual.getEmpleadoNombre() != null) ? facturaActual.getEmpleadoNombre() : "Admin";
                g2d.drawString("Atendido por: " + nombreEmpleado, x, y);
                y += lineHeight;

                y += 4;
                y = drawLine(g2d, "-", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // ========== PRODUCTOS ==========
                g2d.setFont(fontBold);
                g2d.drawString("CANT ARTICULO       TOTAL", x, y);
                y += lineHeight;
                g2d.setFont(fontNormal);
                y = drawLine(g2d, "-", x, y, width, MAX_CHARS_PER_LINE);

                for (LineaFactura linea : facturaActual.getLineas()) {
                    y += 2;

                    String nombre = linea.getNombreProducto();
                    List<String> lineasNombre = dividirTexto(nombre, 14);

                    // Línea 1: Cantidad + Nombre
                    String cantStr = String.format("%-4d", linea.getCantidad());
                    g2d.drawString(cantStr + lineasNombre.get(0), x, y);

                    // Subtotal de la línea (Alineado a la derecha)
                    String subtotal = String.format("%.2f", linea.getTotalConIva());
                    drawRightText(g2d, subtotal, x, y, width);
                    y += lineHeight;

                    // Líneas adicionales del nombre
                    for (int i = 1; i < lineasNombre.size(); i++) {
                        g2d.drawString("    " + lineasNombre.get(i), x, y);
                        y += lineHeight;
                    }

                    // LÓGICA DE DESCUENTO CONDICIONAL
                    if (linea.getDescuento() > 0) {
                        g2d.setFont(fontSmall);
                        String dtoInfo = String.format("    PVP: %.2f (-%d%%)",
                                linea.getPrecioUnitario(), linea.getDescuento());
                        g2d.drawString(dtoInfo, x, y);
                        g2d.setFont(fontNormal);
                        y += lineHeight;
                    }
                }

                y += 4;
                y = drawLine(g2d, "-", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // ========== TOTALES ==========
                y = drawTotalRow(g2d, "Base imponible:", facturaActual.getTotalSinIva(), x, y, width, fontNormal);

                String labelIva = "IVA " + config.getIvaGeneral() + "%:";
                y = drawTotalRow(g2d, labelIva, facturaActual.getTotalIva(), x, y, width, fontNormal);

                y += 2;
                y = drawLine(g2d, "=", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                y = drawTotalRow(g2d, "TOTAL:", facturaActual.getTotalConIva(), x, y, width, fontBold);

                y += 4;
                y = drawLine(g2d, "=", x, y, width, MAX_CHARS_PER_LINE);
                y += lineHeight;

                // ========== PIE ==========
                y = drawCenteredText(g2d, "Gracias por su compra", x, y, width);
                y = drawCenteredText(g2d, "Vuelva pronto", x, y, width);

            } catch (Exception e) {
                throw new PrinterException(e.getMessage());
            }

            return PAGE_EXISTS;
        }

        private List<String> dividirTexto(String texto, int max) {
            List<String> res = new ArrayList<>();
            if (texto == null) return res;
            if (texto.length() <= max) { res.add(texto); return res; }

            String[] words = texto.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (sb.length() + w.length() + 1 <= max) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(w);
                } else {
                    if (sb.length() > 0) res.add(sb.toString());
                    sb = new StringBuilder(w);
                }
            }
            if (sb.length() > 0) res.add(sb.toString());
            return res;
        }

        private int drawCenteredText(Graphics2D g2d, String text, int x, int y, int width) {
            if (text == null || text.isEmpty()) return y;
            FontMetrics fm = g2d.getFontMetrics();
            int tw = fm.stringWidth(text);
            g2d.drawString(text, x + (width - tw) / 2, y);
            return y + 12;
        }

        private void drawRightText(Graphics2D g2d, String text, int x, int y, int width) {
            FontMetrics fm = g2d.getFontMetrics();
            int tw = fm.stringWidth(text);
            g2d.drawString(text, x + width - tw, y);
        }

        private int drawTotalRow(Graphics2D g2d, String label, BigDecimal value, int x, int y, int width, Font font) {
            g2d.setFont(font);
            g2d.drawString(label, x, y);
            drawRightText(g2d, String.format("%.2f", value), x, y, width);
            return y + 12;
        }

        private int drawLine(Graphics2D g2d, String c, int x, int y, int width, int n) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append(c);
            g2d.drawString(sb.toString(), x, y);
            return y + 12;
        }
    }
}
