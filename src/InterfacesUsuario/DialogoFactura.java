package InterfacesUsuario;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;
import objetosNegocio.Comprobante;
import objetosServicio.InformePdf;

/** Vista de una factura con opción de guardarla en PDF. */
public class DialogoFactura extends JDialog {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final Comprobante factura;

    public DialogoFactura(Window padre, Comprobante factura) {
        super(padre, "Factura " + factura.getNumero(), Dialog.ModalityType.APPLICATION_MODAL);
        this.factura = factura;
        setIconImage(IconoApp.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado("Factura N.º " + factura.getNumero(), factura.getTipoEtiqueta() + " · " + factura.getFecha().format(FORMATO)), BorderLayout.NORTH);

        JPanel datos = Tema.panelFormulario();
        int f = 0;
        f = fila(datos, f, "Cliente", factura.getNombreTitular());
        f = fila(datos, f, "Cédula", factura.getCedulaTitular());
        f = fila(datos, f, "Emitida por", factura.getEmisor());
        f = fila(datos, f, "Concepto", factura.getConcepto());
        JTextArea detalle = new JTextArea(factura.getDetalle());
        detalle.setEditable(false);
        detalle.setLineWrap(true);
        detalle.setWrapStyleWord(true);
        detalle.setFont(Tema.fuente(Font.PLAIN, 13f));
        detalle.setBackground(Color.WHITE);
        detalle.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(detalle);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        scroll.setPreferredSize(new Dimension(380, 150));
        Tema.fila(datos, f, "Detalle", scroll);

        JLabel total = new JLabel("TOTAL  " + String.format("$%.2f", factura.getTotal()), JLabel.RIGHT);
        total.setFont(Tema.fuente(Font.BOLD, 22f));
        total.setForeground(Color.BLACK);

        JButton pdf = Tema.boton("Descargar PDF", true);
        pdf.addActionListener(e -> guardarPdf());
        JButton cerrar = Tema.boton("Cerrar", false);
        cerrar.addActionListener(e -> dispose());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(pdf);
        botones.add(cerrar);

        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(BorderFactory.createEmptyBorder(16, 26, 16, 26));
        centro.add(datos, BorderLayout.NORTH);
        centro.add(total, BorderLayout.CENTER);
        centro.add(botones, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);
        setPreferredSize(new Dimension(620, 640));
        pack();
        setLocationRelativeTo(padre);
    }

    private static int fila(JPanel p, int fila, String etiqueta, String valor) {
        JLabel v = Tema.etiqueta(valor == null || valor.isEmpty() ? "—" : valor);
        v.setFont(Tema.fuente(Font.BOLD, 13f));
        Tema.fila(p, fila, etiqueta, v);
        return fila + 1;
    }

    private void guardarPdf() {
        Descargas.guardarPdf(this, "Descargar factura", control.ComprobantesPdf.nombreSugerido("Factura", factura.getNumero()),
                destino -> new control.ComprobantesPdf().factura(destino, factura));
    }
}
