package InterfacesUsuario;

import control.ControladorBilletera;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import objetosNegocio.Usuario;

/** Portal del pasajero: comprar pasajes, administrar la billetera, ver sus pasajes y sus facturas. */
public class PantallaPortalPasajero extends JFrame {
    private final Usuario usuario;
    private final ControladorBilletera billetera = new ControladorBilletera();
    private final JLabel lblSaldo = new JLabel();
    private PanelComprarPasaje panelComprar;
    private PanelBilletera panelBilletera;
    private PanelMisPasajes panelPasajes;
    private PanelFacturacion panelFacturas;

    public PantallaPortalPasajero(Usuario usuario) {
        this.usuario = usuario;
        setTitle(objetosServicio.Marca.NOMBRE + " - Pasajero: " + usuario.getNombre());
        setIconImage(IconoApp.crear(64));
        Navegador.confirmarAlSalir(this);
        setLayout(new BorderLayout());
        lblSaldo.setForeground(new Color(0x0B6B2E));
        lblSaldo.setFont(Tema.fuente(Font.BOLD, 16f));
        add(BarraSuperior.crear(this, usuario, lblSaldo, this::actualizarCabecera), BorderLayout.NORTH);

        Runnable refrescar = this::refrescarTodo;
        panelComprar = new PanelComprarPasaje(usuario, refrescar);
        panelBilletera = new PanelBilletera(usuario, refrescar);
        panelPasajes = new PanelMisPasajes(usuario, refrescar);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Tema.fuente(Font.BOLD, 14f));
        tabs.addTab("Comprar pasaje", panelComprar);
        tabs.addTab("Mi billetera", panelBilletera);
        tabs.addTab("Mis pasajes", panelPasajes);
        panelFacturas = new PanelFacturacion(usuario, false);
        tabs.addTab("Mis facturas", conBusqueda(panelFacturas, panelFacturas.getTabla()));
        Tema.pestanas(tabs);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() != panelComprar && tabs.getSelectedIndex() == 3) {
                panelFacturas.cargar();
            }
        });
        add(tabs, BorderLayout.CENTER);
        actualizarCabecera();
        setPreferredSize(new Dimension(1000, 680));
        pack();
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);
    }

    /** Agrega la barra de búsqueda en vivo sobre una tabla. */
    private static JPanel conBusqueda(JPanel panel, javax.swing.JTable tabla) {
        JPanel envoltura = new JPanel(new BorderLayout());
        envoltura.setOpaque(false);
        BarraBusqueda barra = BarraBusqueda.para(tabla);
        if (barra != null) {
            envoltura.add(barra, BorderLayout.NORTH);
        }
        envoltura.add(panel, BorderLayout.CENTER);
        return envoltura;
    }

    private void actualizarCabecera() {
        ResultadoOperacion s = billetera.consultarSaldo(usuario.getIdPerfil());
        lblSaldo.setText("Saldo: " + (s.isExito() ? Tema.dinero((Double) s.getDatos()) : "—"));
    }

    private void refrescarTodo() {
        actualizarCabecera();
        panelComprar.recargar();
        panelBilletera.recargar();
        panelPasajes.recargar();
        panelFacturas.cargar();
    }
}
