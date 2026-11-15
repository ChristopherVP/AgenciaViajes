package control;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Foto de perfil de conductores y pasajeros: solo PNG o JPG; se recorta cuadrada y se guarda en fotos/ como PNG de 256 px. */
public class ControladorFotos {
    public static final int LADO = 256;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final File CARPETA = new File("fotos");

    /** null si el archivo sirve; si no, el motivo. */
    public static String errorArchivo(File f) {
        if (f == null || !f.isFile()) {
            return "Elige un archivo de imagen";
        }
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) {
            return "Solo se aceptan fotos PNG o JPG";
        }
        if (f.length() > MAX_BYTES) {
            return "La foto pesa más de 8 MB";
        }
        try {
            if (ImageIO.read(f) == null) {
                return "El archivo no es una imagen válida (aunque termine en .png o .jpg)";
            }
        } catch (IOException e) {
            return "No se pudo leer la imagen: " + e.getMessage();
        }
        return null;
    }

    private static File archivoDe(String idUsuario) {
        return new File(CARPETA, idUsuario.replaceAll("[^A-Za-z0-9_-]", "") + ".png");
    }

    public ResultadoOperacion guardar(String idUsuario, File origen) {
        String error = errorArchivo(origen);
        if (error != null) {
            return new ResultadoOperacion(false, error);
        }
        try {
            BufferedImage img = ImageIO.read(origen);
            int lado = Math.min(img.getWidth(), img.getHeight());
            BufferedImage recorte = img.getSubimage((img.getWidth() - lado) / 2, (img.getHeight() - lado) / 2, lado, lado);
            BufferedImage fin = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = fin.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(recorte, 0, 0, LADO, LADO, null);
            g.dispose();
            if (!CARPETA.isDirectory() && !CARPETA.mkdirs()) {
                return new ResultadoOperacion(false, "No se pudo crear la carpeta de fotos");
            }
            ImageIO.write(fin, "png", archivoDe(idUsuario));
            return new ResultadoOperacion(true, "Foto de perfil actualizada", fin);
        } catch (IOException | RuntimeException e) {
            return new ResultadoOperacion(false, "No se pudo guardar la foto: " + e.getMessage());
        }
    }

    /** La foto guardada o null si la persona no ha subido ninguna. */
    public BufferedImage cargar(String idUsuario) {
        try {
            File f = archivoDe(idUsuario);
            return f.isFile() ? ImageIO.read(f) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public ResultadoOperacion quitar(String idUsuario) {
        File f = archivoDe(idUsuario);
        if (f.isFile() && !f.delete()) {
            return new ResultadoOperacion(false, "No se pudo borrar la foto");
        }
        return new ResultadoOperacion(true, "Foto eliminada");
    }
}
