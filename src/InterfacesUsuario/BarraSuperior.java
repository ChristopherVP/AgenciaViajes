package InterfacesUsuario;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import objetosNegocio.Usuario;

/**
 * Barra superior igual para administradores, pasajeros y conductores: quién ingresó (y su cargo o rol), un dato opcional
 * en el centro (por ejemplo el saldo), botones propios de cada pantalla y, siempre, "Mi perfil" y "Cerrar sesión".
 */
public final class BarraSuperior {
    private BarraSuperior() {
    }

    public static JPanel crear(Window ventana, Usuario usuario, JComponent centro, Runnable alActualizarPerfil, JButton... extras) {
        JPanel barra = new JPanel(new BorderLayout(12, 0));
        barra.setBackground(Color.WHITE);
        barra.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        String cargo = usuario.getCargo() == null || usuario.getCargo().isEmpty() ? usuario.getTipo() : usuario.getCargo();
        JLabel saludo = new JLabel("Hola, " + usuario.getNombre() + "  ·  " + cargo);
        saludo.setFont(Tema.fuente(Font.BOLD, 15f));
        saludo.setForeground(Color.BLACK);
        barra.add(saludo, BorderLayout.WEST);
        if (centro != null) {
            JPanel c = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
            c.setOpaque(false);
            c.add(centro);
            barra.add(c, BorderLayout.CENTER);
        }
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        for (JButton extra : extras) {
            botones.add(extra);
        }
        JButton perfil = Tema.boton("Mi perfil", false);
        perfil.addActionListener(e -> new DialogoPerfil(ventana, usuario, alActualizarPerfil == null ? () -> { } : alActualizarPerfil).setVisible(true));
        JButton salir = Tema.boton("Cerrar sesión", true);
        salir.addActionListener(e -> Navegador.cerrarSesion(ventana));
        botones.add(perfil);
        botones.add(salir);
        barra.add(botones, BorderLayout.EAST);
        return barra;
    }
}
