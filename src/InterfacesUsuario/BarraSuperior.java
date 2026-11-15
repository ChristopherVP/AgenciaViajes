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

    /** Foto de perfil recortada en círculo; si la persona no subió una, un círculo con sus iniciales. */
    public static javax.swing.ImageIcon avatar(Usuario usuario, int tamano) {
        java.awt.image.BufferedImage foto = usuario.getIdPerfil() == null || usuario.getIdPerfil().isEmpty() ? null
                : new control.ControladorFotos().cargar(usuario.getIdPerfil());
        java.awt.image.BufferedImage salida = new java.awt.image.BufferedImage(tamano, tamano, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = salida.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, tamano, tamano));
        if (foto != null) {
            g.drawImage(foto, 0, 0, tamano, tamano, null);
        } else {
            g.setColor(Tema.AZUL);
            g.fillRect(0, 0, tamano, tamano);
            String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
            String iniciales = nombre.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(nombre.charAt(0)));
            int sp = nombre.lastIndexOf(' ');
            if (sp > 0 && sp + 1 < nombre.length()) {
                iniciales += Character.toUpperCase(nombre.charAt(sp + 1));
            }
            g.setColor(Color.WHITE);
            g.setFont(Tema.fuente(Font.BOLD, tamano * 0.4f));
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString(iniciales, (tamano - fm.stringWidth(iniciales)) / 2f, (tamano + fm.getAscent() - fm.getDescent()) / 2f);
        }
        g.dispose();
        return new javax.swing.ImageIcon(salida);
    }

    /** Vuelve a dibujar la foto de la barra superior de la ventana (después de subirla o quitarla en Mi perfil). */
    public static void refrescarAvatar(java.awt.Container raiz) {
        if (raiz == null) {
            return;
        }
        for (java.awt.Component c : raiz.getComponents()) {
            if (c instanceof JLabel l && l.getClientProperty("avatarUsuario") instanceof Usuario u) {
                l.setIcon(avatar(u, 40));
            } else if (c instanceof java.awt.Container hijo) {
                refrescarAvatar(hijo);
            }
        }
        if (raiz instanceof javax.swing.JFrame f) {
            refrescarAvatar(f.getContentPane());
        }
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
        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        izquierda.setOpaque(false);
        JLabel avatar = new JLabel(avatar(usuario, 40));
        avatar.putClientProperty("avatarUsuario", usuario);
        izquierda.add(avatar);
        izquierda.add(saludo);
        barra.add(izquierda, BorderLayout.WEST);
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
