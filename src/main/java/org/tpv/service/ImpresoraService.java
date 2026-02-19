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
 * Versión MEJORADA del servicio de impresión.
 * - Texto con tono más oscuro para mejor legibilidad (sin negrita).
 * - Tabla rediseñada: Cantidad, Artículo, Total (alineación optimizada).
 * - Añadido contador de artículos: "Nº Artículos: X".
 */
public class ImpresoraService {

    private final Configuracion config;
    private Factura facturaActual;

    private static final double ANCHO_PAPEL_PUNTOS = 138.0;
    private static final int MARGEN_IZQUIERDO = 5;
    private static final int MARGEN_DERECHO = 2; // Ajustado ligeramente
    private static final int ANCHO_IMPRIMIBLE = (int)(ANCHO_PAPEL_PUNTOS - MARGEN_IZQUIERDO - MARGEN_DERECHO);

    private static final int MAX_CHARS_PER_LINE = 38;

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
            // Mejorar calidad del renderizado de texto
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            int x = (int) pageFormat.getImageableX();
            int y = 15;
            int width = (int) pageFormat.getImageableWidth();

            // Fuentes uniformes
            Font fontNormal = new Font("SansSerif", Font.PLAIN, 9);
            Font fontTitle = new Font("SansSerif", Font.PLAIN, 11);
            Font fontSmall = new Font("SansSerif", Font.PLAIN, 8);

            // Tono de texto más oscuro (Negro puro con alta calidad de renderizado)
            g2d.setColor(Color.BLACK);

            try {
                // ========== ENCABEZADO ==========
                g2d.setFont(fontTitle);
                y = drawText(g2d, config.getNombreTienda(), x, y, width, "left");

                g2d.setFont(fontSmall);
                y = drawText(g2d, config.getDireccion(), x, y, width, "left");
                y = drawText(g2d, config.getCodigoPostal() + " " + config.getCiudad(), x, y, width, "left");
                y = drawText(g2d, "Tel: " + config.getTelefono(), x, y, width, "left");

                if (config.getCif() != null && !config.getCif().isEmpty()) {
                    y = drawText(g2d, "CIF: " + config.getCif(), x, y, width, "left");
                }
                if (config.getNif() != null && !config.getNif().isEmpty()) {
                    y = drawText(g2d, "NIF: " + config.getNif(), x, y, width, "left");
                }

                y += 4;
                y = drawLine(g2d, "=", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // ========== INFO FACTURA ==========
                g2d.setFont(fontNormal);
                y = drawText(g2d, "FACTURA SIMPLIFICADA", x, y, width, "left");

                if (facturaActual.getNumeroFactura() != null) {
                    y = drawText(g2d, "No: " + facturaActual.getNumeroFactura(), x, y, width, "left");
                }

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                y = drawText(g2d, "Fecha: " + facturaActual.getFechaEmision().format(formatter), x, y, width, "left");

                String nombreCliente = (facturaActual.getClienteNombre() != null) ? facturaActual.getClienteNombre() : "AL CONTADO";
                List<String> lineasCliente = dividirTexto("Cliente: " + nombreCliente, 25);
                for (String linea : lineasCliente) {
                    y = drawText(g2d, linea, x, y, width, "left");
                }

                String nombreEmpleado = (facturaActual.getEmpleadoNombre() != null) ? facturaActual.getEmpleadoNombre() : "Admin";
                y = drawText(g2d, "Atendido por: " + nombreEmpleado, x, y, width, "left");

                y += 4;
                y = drawLine(g2d, "-", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // ========== PRODUCTOS (TABLA REDISEÑADA) ==========
                g2d.setFont(fontNormal);
                // Encabezados con alineación específica
                drawText(g2d, "CANT", x, y, 30, "left");
                drawText(g2d, "ARTICULO", x + 30, y, width - 65, "left");
                drawText(g2d, "TOTAL", x, y, width, "right");
                y += g2d.getFontMetrics().getHeight();

                y = drawLine(g2d, "-", x, y, width, MAX_CHARS_PER_LINE);

                int totalArticulos = 0;
                for (LineaFactura linea : facturaActual.getLineas()) {
                    y += 2;
                    String nombre = linea.getNombreProducto();
                    // Dividimos el nombre ajustando el espacio disponible
                    List<String> lineasNombre = dividirTexto(nombre, 18);

                    String cantStr = String.valueOf(linea.getCantidad());
                    String subtotal = String.format("%.2f", linea.getTotalConIva());
                    totalArticulos += linea.getCantidad();

                    // Fila de producto: Cantidad | Nombre | Total
                    drawText(g2d, cantStr, x, y, 30, "left");
                    drawText(g2d, lineasNombre.get(0), x + 30, y, width - 65, "left");
                    drawText(g2d, subtotal, x, y, width, "right");
                    y += g2d.getFontMetrics().getHeight();

                    // Líneas adicionales del nombre
                    for (int i = 1; i < lineasNombre.size(); i++) {
                        y = drawText(g2d, lineasNombre.get(i), x + 30, y, width - 65, "left");
                    }

                    if (linea.getDescuento() > 0) {
                        g2d.setFont(fontSmall);
                        String dtoInfo = String.format("PVP: %.2f (-%d%%)",
                                linea.getPrecioUnitario(), linea.getDescuento());
                        y = drawText(g2d, dtoInfo, x + 30, y, width - 65, "left");
                        g2d.setFont(fontNormal);
                    }
                }

                y += 4;
                y = drawLine(g2d, "-", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // ========== CONTADOR DE ARTÍCULOS ==========
                String totalArtStr = String.format("%d", totalArticulos);
                drawText(g2d, "Nº Artículos:", x, y, width, "left");
                y = drawText(g2d, totalArtStr, x + 100, y, width - 100, "left");
                y += 4;

                // ========== TOTALES ==========
                y = drawTotalRow(g2d, "Base imponible:", facturaActual.getTotalSinIva(), x, y, width, fontNormal);
                String labelIva = "IVA " + config.getIvaGeneral() + "%:";
                y = drawTotalRow(g2d, labelIva, facturaActual.getTotalIva(), x, y, width, fontNormal);

                y += 2;
                y = drawLine(g2d, "=", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // TOTAL
                y = drawTotalRow(g2d, "TOTAL:", facturaActual.getTotalConIva(), x, y, width, fontNormal);

                y += 4;
                y = drawLine(g2d, "=", x, y, width, MAX_CHARS_PER_LINE);
                y += 10;

                // ========== PIE ==========
                g2d.setFont(fontNormal);
                y = drawText(g2d, "Gracias por su compra", x, y, width, "center");
                y = drawText(g2d, "Vuelva pronto", x, y, width, "center");

            } catch (Exception e) {
                throw new PrinterException(e.getMessage());
            }

            return PAGE_EXISTS;
        }

        private int drawText(Graphics2D g2d, String text, int x, int y, int width, String align) {
            if (text == null || text.isEmpty()) return y;
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int startX = x;

            if ("center".equalsIgnoreCase(align)) {
                startX = x + (width - textWidth) / 2;
            } else if ("right".equalsIgnoreCase(align)) {
                startX = x + width - textWidth;
            }

            g2d.drawString(text, (float)startX, (float)y);
            return ("right".equalsIgnoreCase(align)) ? y : y + fm.getHeight();
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

        private int drawTotalRow(Graphics2D g2d, String label, BigDecimal value, int x, int y, int width, Font font) {
            g2d.setFont(font);
            drawText(g2d, label, x, y, width, "left");
            drawText(g2d, String.format("%.2f", value), x, y, width, "right");
            return y + g2d.getFontMetrics().getHeight();
        }

        private int drawLine(Graphics2D g2d, String c, int x, int y, int width, int n) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append(c);
            return drawText(g2d, sb.toString(), x, y, width, "left");
        }
    }
}
