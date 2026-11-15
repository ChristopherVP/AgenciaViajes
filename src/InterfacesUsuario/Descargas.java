package InterfacesUsuario;

import java.awt.Component;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Diálogo común para descargar un PDF (factura, pasaje, movimiento de billetera, pago a conductor). */
public final class Descargas {
    /** Escribe el PDF en el archivo elegido. */
    public interface Escritor {
        void escribir(File destino) throws IOException;
    }

    private Descargas() {
    }

    public static void guardarPdf(Component padre, String titulo, String nombreSugerido, Escritor escritor) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle(titulo);
        selector.setFileFilter(new FileNameExtensionFilter("PDF (*.pdf)", "pdf"));
        File descargas = new File(System.getProperty("user.home"), "Downloads");
        if (descargas.isDirectory()) {
            selector.setCurrentDirectory(descargas);
        }
        selector.setSelectedFile(new File(selector.getCurrentDirectory(), nombreSugerido));
        if (selector.showSaveDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destino = selector.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith(".pdf")) {
            destino = new File(destino.getParentFile(), destino.getName() + ".pdf");
        }
        if (destino.exists() && JOptionPane.showConfirmDialog(padre, "Ya existe " + destino.getName() + ". ¿Reemplazarlo?", titulo,
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            escritor.escribir(destino);
        } catch (IOException | RuntimeException e) {
            Tema.error(padre, "No se pudo guardar el PDF: " + e.getMessage());
            return;
        }
        Object[] opciones = {"Abrir", "Cerrar"};
        if (JOptionPane.showOptionDialog(padre, "PDF guardado en:\n" + destino.getAbsolutePath(), titulo, JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, opciones, opciones[1]) == 0) {
            try {
                Desktop.getDesktop().open(destino);
            } catch (Exception e) {
                Tema.info(padre, "Ábrelo desde la carpeta indicada.");
            }
        }
    }
}
