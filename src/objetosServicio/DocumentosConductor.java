package objetosServicio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Documentos que un conductor adjunta al registrarse (cédula, licencia, antecedentes, certificado médico).
 * Se guardan en la carpeta "documentos/&lt;cédula&gt;/" junto al programa para que el administrador los revise.
 */
public final class DocumentosConductor {
    public enum Tipo {
        CEDULA("cedula", "Copia de cédula", true),
        LICENCIA("licencia", "Licencia de conducir profesional", true),
        ANTECEDENTES("antecedentes", "Certificado de antecedentes penales", true),
        MEDICO("medico", "Certificado médico (opcional)", false);

        private final String archivo;
        private final String etiqueta;
        private final boolean obligatorio;

        Tipo(String archivo, String etiqueta, boolean obligatorio) {
            this.archivo = archivo;
            this.etiqueta = etiqueta;
            this.obligatorio = obligatorio;
        }

        public String getEtiqueta() { return etiqueta; }
        public boolean esObligatorio() { return obligatorio; }
    }

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String[] EXTENSIONES = {"pdf", "jpg", "jpeg", "png"};
    private static final Path BASE = Path.of("documentos");

    private DocumentosConductor() {
    }

    private static Path carpeta(String cedula) {
        if (cedula == null || !cedula.matches("\\d{10}")) {
            throw new IllegalArgumentException("Cédula inválida");
        }
        return BASE.resolve(cedula);
    }

    /** null si el archivo sirve; si no, el motivo. Se comprueban extensión, tamaño y que el contenido corresponda al formato. */
    public static String errorArchivo(File f) {
        if (f == null || !f.isFile() || !f.canRead()) {
            return "No se puede leer el archivo";
        }
        String ext = extension(f.getName());
        boolean permitida = false;
        for (String e : EXTENSIONES) {
            permitida |= e.equals(ext);
        }
        if (!permitida) {
            return "Solo se aceptan archivos PDF, JPG o PNG";
        }
        if (f.length() == 0) {
            return "El archivo está vacío";
        }
        if (f.length() > MAX_BYTES) {
            return "El archivo supera los 5 MB";
        }
        try (InputStream in = Files.newInputStream(f.toPath())) {
            byte[] c = in.readNBytes(8);
            boolean pdf = c.length >= 4 && c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F';
            boolean jpg = c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
            boolean png = c.length >= 4 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G';
            boolean coincide = ext.equals("pdf") ? pdf : (ext.equals("png") ? png : jpg);
            if (!coincide) {
                return "El contenido del archivo no corresponde a un " + ext.toUpperCase(Locale.ROOT) + " válido";
            }
        } catch (IOException e) {
            return "No se puede leer el archivo";
        }
        return null;
    }

    public static void guardar(String cedula, Tipo tipo, File origen) throws IOException {
        Path dir = carpeta(cedula);
        Files.createDirectories(dir);
        for (String e : EXTENSIONES) {
            Files.deleteIfExists(dir.resolve(tipo.archivo + "." + e));
        }
        Files.copy(origen.toPath(), dir.resolve(tipo.archivo + "." + extension(origen.getName())), StandardCopyOption.REPLACE_EXISTING);
    }

    public static Map<Tipo, Path> listar(String cedula) {
        Map<Tipo, Path> mapa = new EnumMap<>(Tipo.class);
        Path dir;
        try {
            dir = carpeta(cedula);
        } catch (IllegalArgumentException e) {
            return mapa;
        }
        for (Tipo t : Tipo.values()) {
            for (String e : EXTENSIONES) {
                Path p = dir.resolve(t.archivo + "." + e);
                if (Files.isRegularFile(p)) {
                    mapa.put(t, p);
                }
            }
        }
        return mapa;
    }

    public static boolean completos(String cedula) {
        Map<Tipo, Path> docs = listar(cedula);
        for (Tipo t : Tipo.values()) {
            if (t.obligatorio && !docs.containsKey(t)) {
                return false;
            }
        }
        return true;
    }

    public static void guardarDatos(String cedula, Properties datos) throws IOException {
        Path dir = carpeta(cedula);
        Files.createDirectories(dir);
        try (OutputStream out = Files.newOutputStream(dir.resolve("datos.properties"))) {
            datos.store(out, "Datos del conductor");
        }
    }

    public static Properties leerDatos(String cedula) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(carpeta(cedula).resolve("datos.properties"))) {
            p.load(in);
        } catch (IOException | IllegalArgumentException e) {
            // sin datos adicionales
        }
        return p;
    }

    private static String extension(String nombre) {
        int i = nombre.lastIndexOf('.');
        return i < 0 ? "" : nombre.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
