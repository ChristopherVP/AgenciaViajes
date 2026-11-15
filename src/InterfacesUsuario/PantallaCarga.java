package InterfacesUsuario;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

/** Ventanita "Iniciando sesión..." que se muestra mientras se prepara la cuenta, para que la aplicación nunca parezca cerrada. */
public class PantallaCarga extends JDialog {
    public PantallaCarga(Window padre, String mensaje) {
        super(padre);
        setUndecorated(true);
        setModalityType(ModalityType.MODELESS);
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Tema.BORDE, 2), BorderFactory.createEmptyBorder(24, 32, 24, 32)));
        JLabel logo = new JLabel(IconoApp.icono(72), SwingConstants.CENTER);
        JLabel texto = new JLabel(mensaje, SwingConstants.CENTER);
        texto.setFont(Tema.fuente(Font.BOLD, 17f));
        texto.setForeground(Color.BLACK);
        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);
        p.add(logo, BorderLayout.NORTH);
        p.add(texto, BorderLayout.CENTER);
        p.add(barra, BorderLayout.SOUTH);
        setContentPane(p);
        setPreferredSize(new Dimension(340, 230));
        pack();
        setLocationRelativeTo(padre);
        setAlwaysOnTop(true);
    }

    public void mostrar() {
        setVisible(true);
        paintImmediately();
    }

    private void paintImmediately() {
        ((javax.swing.JComponent) getContentPane()).paintImmediately(0, 0, getWidth(), getHeight());
    }

    public void cerrar() {
        dispose();
    }
}
