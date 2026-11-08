package InterfacesUsuario;

import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Guarda en PDF o Excel (.xlsx) lo que muestra una tabla (incluida la búsqueda aplicada). No necesita librerías
 * externas: el .xlsx se arma como un zip de XML y el PDF se escribe directamente con la fuente estándar Helvetica.
 */
public final class ExportadorTabla {
    public enum Formato {
        PDF("pdf", "PDF"), EXCEL("xlsx", "Excel");

        final String extension;
        final String nombre;

        Formato(String extension, String nombre) {
            this.extension = extension;
            this.nombre = nombre;
        }
    }

    private ExportadorTabla() {
    }

    // ------------------------------------------------------------------ interfaz

    /** Busca la primera tabla dentro de un componente (por ejemplo la pestaña elegida). */
    public static JTable buscarTabla(Component c) {
        if (c instanceof JTable t) {
            return t;
        }
        if (c instanceof Container cont) {
            for (Component hijo : cont.getComponents()) {
                JTable t = buscarTabla(hijo);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    /** Pide el lugar donde guardar y escribe el archivo. Muestra el resultado al usuario. */
    public static void exportar(Component padre, JTable tabla, String titulo, Formato formato) {
        if (tabla == null || tabla.getRowCount() == 0) {
            Tema.error(padre, "No hay datos en la tabla para guardar.");
            return;
        }
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Guardar como " + formato.nombre);
        selector.setFileFilter(new FileNameExtensionFilter(formato.nombre + " (*." + formato.extension + ")", formato.extension));
        String base = Normalizer.normalize(titulo, Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        selector.setSelectedFile(new File(base + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + "." + formato.extension));
        if (selector.showSaveDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destino = selector.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith("." + formato.extension)) {
            destino = new File(destino.getParentFile(), destino.getName() + "." + formato.extension);
        }
        if (destino.exists() && JOptionPane.showConfirmDialog(padre, "El archivo ya existe. ¿Quieres reemplazarlo?", "Reemplazar archivo",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            List<String> cabecera = cabecera(tabla);
            List<List<String>> filas = filas(tabla);
            if (formato == Formato.PDF) {
                escribirPdf(destino, titulo, cabecera, filas);
            } else {
                escribirXlsx(destino, titulo, cabecera, filas);
            }
        } catch (IOException e) {
            Tema.error(padre, "No se pudo guardar el archivo: " + e.getMessage());
            return;
        }
        Object[] opciones = {"Abrir archivo", "Cerrar"};
        int r = JOptionPane.showOptionDialog(padre, "Se guardaron " + tabla.getRowCount() + " fila(s) en:\n" + destino.getAbsolutePath(),
                "Archivo guardado", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opciones, opciones[1]);
        if (r == 0) {
            try {
                Desktop.getDesktop().open(destino);
            } catch (Exception e) {
                Tema.info(padre, "Ábrelo manualmente desde la carpeta indicada.");
            }
        }
    }

    public static List<String> cabecera(JTable t) {
        List<String> cab = new ArrayList<>();
        for (int c = 0; c < t.getColumnCount(); c++) {
            cab.add(t.getColumnName(c));
        }
        return cab;
    }

    public static List<List<String>> filas(JTable t) {
        List<List<String>> filas = new ArrayList<>();
        for (int r = 0; r < t.getRowCount(); r++) {
            List<String> fila = new ArrayList<>();
            for (int c = 0; c < t.getColumnCount(); c++) {
                Object v = t.getValueAt(r, c);
                fila.add(v == null ? "" : v.toString());
            }
            filas.add(fila);
        }
        return filas;
    }

    // ------------------------------------------------------------------ Excel (.xlsx)

    public static void escribirXlsx(File destino, String titulo, List<String> cabecera, List<List<String>> filas) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(destino))) {
            entrada(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "</Types>");
            entrada(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            entrada(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"Datos\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entrada(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            entrada(zip, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
                    + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDCE8F2\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                    + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"
                    + "<border><left style=\"thin\"><color rgb=\"FF9AAAB8\"/></left><right style=\"thin\"><color rgb=\"FF9AAAB8\"/></right>"
                    + "<top style=\"thin\"><color rgb=\"FF9AAAB8\"/></top><bottom style=\"thin\"><color rgb=\"FF9AAAB8\"/></bottom><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                    + "</styleSheet>");

            StringBuilder hoja = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols>");
            for (int c = 0; c < cabecera.size(); c++) {
                int max = cabecera.get(c).length();
                for (List<String> fila : filas) {
                    max = Math.max(max, fila.get(c).length());
                }
                hoja.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
                        .append(Math.min(60, Math.max(10, max + 3))).append("\" customWidth=\"1\"/>");
            }
            hoja.append("</cols><sheetData>");
            fila(hoja, 1, List.of(titulo), 3, false);
            fila(hoja, 2, List.of("Generado el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " · " + filas.size() + " registro(s)"), 0, false);
            fila(hoja, 4, cabecera, 1, false);
            int n = 5;
            for (List<String> f : filas) {
                fila(hoja, n++, f, 2, true);
            }
            hoja.append("</sheetData></worksheet>");
            entrada(zip, "xl/worksheets/sheet1.xml", hoja.toString());
        }
    }

    private static void fila(StringBuilder sb, int numero, List<String> celdas, int estilo, boolean numeros) {
        sb.append("<row r=\"").append(numero).append("\">");
        for (int c = 0; c < celdas.size(); c++) {
            String ref = columna(c) + numero;
            String v = celdas.get(c);
            if (numeros && v.matches("-?(0|[1-9]\\d{0,14})(\\.\\d+)?")) {
                sb.append("<c r=\"").append(ref).append("\" s=\"").append(estilo).append("\"><v>").append(v).append("</v></c>");
            } else {
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\" s=\"").append(estilo).append("\"><is><t xml:space=\"preserve\">")
                        .append(xml(v)).append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    private static String columna(int indice) {
        StringBuilder s = new StringBuilder();
        for (int i = indice; i >= 0; i = i / 26 - 1) {
            s.insert(0, (char) ('A' + i % 26));
        }
        return s.toString();
    }

    private static String xml(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '&') sb.append("&amp;");
            else if (ch == '<') sb.append("&lt;");
            else if (ch == '>') sb.append("&gt;");
            else if (ch == '"') sb.append("&quot;");
            else if (ch >= 0x20 || ch == '\t' || ch == '\n') sb.append(ch);
        }
        return sb.toString();
    }

    private static void entrada(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // ------------------------------------------------------------------ PDF

    private static final float ANCHO_PAGINA = 842f;   // A4 horizontal
    private static final float ALTO_PAGINA = 595f;
    private static final float MARGEN = 36f;
    private static final float ALTO_FILA = 17f;
    private static final float TAMANO = 8f;

    public static void escribirPdf(File destino, String titulo, List<String> cabecera, List<List<String>> filas) throws IOException {
        int columnas = cabecera.size();
        float util = ANCHO_PAGINA - 2 * MARGEN;
        double[] peso = new double[columnas];
        double total = 0;
        for (int c = 0; c < columnas; c++) {
            int max = cabecera.get(c).length();
            for (List<String> f : filas) {
                max = Math.max(max, f.get(c).length());
            }
            peso[c] = Math.min(max, 32) + 3;
            total += peso[c];
        }
        float[] ancho = new float[columnas];
        for (int c = 0; c < columnas; c++) {
            ancho[c] = (float) (util * peso[c] / total);
        }

        float yInicio = ALTO_PAGINA - MARGEN - 52;
        int filasPorPagina = (int) ((yInicio - MARGEN - 18) / ALTO_FILA) - 1;
        int paginas = Math.max(1, (int) Math.ceil(filas.size() / (double) filasPorPagina));
        String generado = "Generado el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "  ·  " + filas.size() + " registro(s)";

        List<String> contenidos = new ArrayList<>();
        for (int p = 0; p < paginas; p++) {
            StringBuilder s = new StringBuilder();
            texto(s, "F2", 15, MARGEN, ALTO_PAGINA - MARGEN - 10, titulo, 0.04f, 0.18f, 0.29f);
            texto(s, "F1", 9, MARGEN, ALTO_PAGINA - MARGEN - 27, generado, 0.35f, 0.4f, 0.45f);
            s.append("0.96 0.65 0.14 rg ").append(fmt(MARGEN)).append(' ').append(fmt(ALTO_PAGINA - MARGEN - 36)).append(" 60 3 re f\n");
            float y = yInicio;
            // encabezado de la tabla
            s.append("0.86 0.91 0.95 rg ").append(fmt(MARGEN)).append(' ').append(fmt(y - ALTO_FILA + 4)).append(' ').append(fmt(util)).append(' ').append(fmt(ALTO_FILA)).append(" re f\n");
            float x = MARGEN;
            for (int c = 0; c < columnas; c++) {
                texto(s, "F2", TAMANO + 0.5f, x + 3, y - 8, ajustar(cabecera.get(c), ancho[c] - 6, TAMANO + 0.5f), 0, 0, 0);
                x += ancho[c];
            }
            y -= ALTO_FILA;
            for (int i = p * filasPorPagina; i < Math.min(filas.size(), (p + 1) * filasPorPagina); i++) {
                if (i % 2 == 1) {
                    s.append("0.97 0.98 0.99 rg ").append(fmt(MARGEN)).append(' ').append(fmt(y - ALTO_FILA + 4)).append(' ').append(fmt(util)).append(' ').append(fmt(ALTO_FILA)).append(" re f\n");
                }
                x = MARGEN;
                for (int c = 0; c < columnas; c++) {
                    texto(s, "F1", TAMANO, x + 3, y - 8, ajustar(filas.get(i).get(c), ancho[c] - 6, TAMANO), 0.1f, 0.1f, 0.1f);
                    x += ancho[c];
                }
                s.append("0.85 0.88 0.9 RG 0.4 w ").append(fmt(MARGEN)).append(' ').append(fmt(y - ALTO_FILA + 4)).append(" m ")
                        .append(fmt(MARGEN + util)).append(' ').append(fmt(y - ALTO_FILA + 4)).append(" l S\n");
                y -= ALTO_FILA;
            }
            texto(s, "F1", 8, ANCHO_PAGINA / 2 - 30, MARGEN - 12, "Página " + (p + 1) + " de " + paginas, 0.4f, 0.4f, 0.4f);
            contenidos.add(s.toString());
        }

        // objetos: 1 catálogo, 2 páginas, 3 y 4 fuentes, luego (página, contenido) por cada página
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> posiciones = new ArrayList<>();
        escribir(out, "%PDF-1.4\n");
        objeto(out, posiciones, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < paginas; p++) {
            kids.append(5 + 2 * p).append(" 0 R ");
        }
        objeto(out, posiciones, "<< /Type /Pages /Kids [" + kids + "] /Count " + paginas + " >>");
        objeto(out, posiciones, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objeto(out, posiciones, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int p = 0; p < paginas; p++) {
            objeto(out, posiciones, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + fmt(ANCHO_PAGINA) + " " + fmt(ALTO_PAGINA) + "] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (6 + 2 * p) + " 0 R >>");
            byte[] datos = contenidos.get(p).getBytes(StandardCharsets.ISO_8859_1);
            posiciones.add(out.size());
            escribir(out, (posiciones.size()) + " 0 obj\n<< /Length " + datos.length + " >>\nstream\n");
            out.write(datos);
            escribir(out, "\nendstream\nendobj\n");
        }
        int inicioXref = out.size();
        escribir(out, "xref\n0 " + (posiciones.size() + 1) + "\n0000000000 65535 f \n");
        for (int pos : posiciones) {
            escribir(out, String.format("%010d 00000 n \n", pos));
        }
        escribir(out, "trailer\n<< /Size " + (posiciones.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + inicioXref + "\n%%EOF\n");
        try (OutputStream os = new FileOutputStream(destino)) {
            out.writeTo(os);
        }
    }

    private static void objeto(ByteArrayOutputStream out, List<Integer> posiciones, String cuerpo) throws IOException {
        posiciones.add(out.size());
        escribir(out, posiciones.size() + " 0 obj\n" + cuerpo + "\nendobj\n");
    }

    private static void escribir(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void texto(StringBuilder s, String fuente, float tamano, float x, float y, String texto, float r, float g, float b) {
        s.append("BT /").append(fuente).append(' ').append(fmt(tamano)).append(" Tf ").append(fmt(r)).append(' ').append(fmt(g)).append(' ').append(fmt(b))
                .append(" rg ").append(fmt(x)).append(' ').append(fmt(y)).append(" Td (").append(escapar(texto)).append(") Tj ET\n");
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    /** Escapa para PDF y deja solo caracteres de Latin-1 (cubre tildes y ñ); lo demás se cambia por "?". */
    private static String escapar(String t) {
        StringBuilder sb = new StringBuilder();
        for (char ch : t.toCharArray()) {
            if (ch == '(' || ch == ')' || ch == '\\') {
                sb.append('\\').append(ch);
            } else if (ch >= 32 && ch <= 255) {
                sb.append(ch);
            } else if (ch == '–' || ch == '—') {
                sb.append('-');
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    /** Recorta el texto con ".." si no cabe en el ancho de la celda. */
    private static String ajustar(String t, float anchoMax, float tamano) {
        if (anchoMedido(t, tamano) <= anchoMax) {
            return t;
        }
        String r = t;
        while (!r.isEmpty() && anchoMedido(r + "..", tamano) > anchoMax) {
            r = r.substring(0, r.length() - 1);
        }
        return r + "..";
    }

    /** Ancho aproximado en puntos con las proporciones de Helvetica. */
    private static float anchoMedido(String t, float tamano) {
        float total = 0;
        for (char ch : t.toCharArray()) {
            float w;
            if (ch == ' ') w = 0.278f;
            else if (Character.isDigit(ch)) w = 0.556f;
            else if ("ijl.,:;'|!".indexOf(ch) >= 0) w = 0.25f;
            else if ("ft()[]-/".indexOf(ch) >= 0) w = 0.33f;
            else if ("mwMW@".indexOf(ch) >= 0) w = 0.85f;
            else if (Character.isUpperCase(ch)) w = 0.68f;
            else w = 0.54f;
            total += w;
        }
        return total * tamano;
    }
}
