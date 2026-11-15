package InterfacesUsuario;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import objetosNegocio.Empresa;
import objetosNegocio.Usuario;
import objetosServicio.Permisos;
import objetosServicio.Permisos.Modulo;
import persistencia.Empresas;

/**
 * Ventana de la administración. Las pestañas que se muestran dependen del cargo de la persona (cada rol tiene su responsabilidad):
 * finanzas ve recargas, facturas y pagos; operaciones ve conductores, buses y viajes; etc. En los módulos donde el cargo solo
 * puede consultar, los botones que modifican datos se ocultan. Cada pestaña tiene búsqueda en vivo.
 */
public class PantallaPrincipal extends JFrame {
    private static final class Pestana {
        final String titulo;
        final String ayuda;
        final Modulo modulo;
        final Color color;
        final Supplier<JPanel> fabrica;
        final boolean sinAgregar;
        final boolean soloLectura;

        Pestana(String titulo, String ayuda, Modulo modulo, Color color, Supplier<JPanel> fabrica, boolean sinAgregar, boolean soloLectura) {
            this.titulo = titulo;
            this.ayuda = ayuda;
            this.modulo = modulo;
            this.color = color;
            this.fabrica = fabrica;
            this.sinAgregar = sinAgregar;
            this.soloLectura = soloLectura;
        }
    }

    /** Botones que modifican datos: se ocultan donde el cargo solo puede consultar. */
    private static final String[] BOTONES_QUE_MODIFICAN = {"agregar", "editar", "eliminar", "nuevo", "aprobar", "rechazar", "revisar", "responder",
            "asignar", "quitar", "pagar", "abordar", "completar", "cancelar", "facturar"};

    private final Usuario usuarioActual;
    private Empresa empresaActual;
    private JMenuBar menuBar;
    private JTabbedPane tabbedPane;
    private PanelGestionBuses panelBuses;
    private PanelGestionRutas panelRutas;
    private PanelGestionViajes panelViajes;
    private PanelGestionReservas panelReservas;
    private PanelGestionPasajeros panelPasajeros;
    private PanelGestionConductores panelConductores;

    public PantallaPrincipal(Usuario usuario) {
        this.usuarioActual = usuario;
        initComponents();
    }

    private List<Pestana> definirPestanas() {
        List<Pestana> p = new ArrayList<>();
        p.add(new Pestana("Empresas", "Empresas de transporte", Modulo.EMPRESAS, new Color(220, 220, 220), () -> new PanelGestionEmpresas(usuarioActual), false, false));
        p.add(new Pestana("Usuarios", "Administradores y aprobación de conductores", Modulo.USUARIOS, new Color(230, 230, 250), () -> new PanelGestionUsuarios(usuarioActual), false, false));
        p.add(new Pestana("Conductores", "Conductores, bus y ruta asignados", Modulo.CONDUCTORES, new Color(255, 215, 0), () -> {
            panelConductores = new PanelGestionConductores(usuarioActual);
            return panelConductores;
        }, true, false));
        p.add(new Pestana("Pasajeros", "Pasajeros registrados", Modulo.PASAJEROS, new Color(255, 228, 196), () -> {
            panelPasajeros = new PanelGestionPasajeros();
            return panelPasajeros;
        }, true, false));
        p.add(new Pestana("Buses", "Flota de buses", Modulo.BUSES, new Color(173, 216, 230), () -> {
            panelBuses = new PanelGestionBuses();
            return panelBuses;
        }, false, false));
        p.add(new Pestana("Rutas", "Rutas directas entre ciudades", Modulo.RUTAS, new Color(152, 251, 152), () -> {
            panelRutas = new PanelGestionRutas();
            return panelRutas;
        }, false, false));
        p.add(new Pestana("Viajes", "Viajes programados", Modulo.VIAJES, new Color(255, 255, 224), () -> {
            panelViajes = new PanelGestionViajes(usuarioActual);
            return panelViajes;
        }, false, false));
        p.add(new Pestana("Reservas", "Pasajes comprados (solo consulta: la compra es definitiva)", Modulo.RESERVAS, new Color(255, 182, 193), () -> {
            panelReservas = new PanelGestionReservas();
            return panelReservas;
        }, false, true));
        p.add(new Pestana("Facturas", "Facturas de todos los movimientos de dinero", Modulo.FACTURAS, new Color(224, 255, 255),
                () -> new PanelFacturacion(usuarioActual, true), false, true));
        p.add(new Pestana("Novedades", "Reportes, quejas y sugerencias de conductores y pasajeros", Modulo.NOVEDADES, new Color(255, 240, 200),
                () -> new PanelNovedadesAdmin(usuarioActual), false, false));
        p.add(new Pestana("Recargas", "Recargas de billetera (el efectivo lo aprueba finanzas)", Modulo.RECARGAS, new Color(200, 240, 200),
                () -> new PanelRecargasAdmin(usuarioActual), false, false));
        p.add(new Pestana("Flota y despacho", "Dónde está cada conductor y asignación de viajes", Modulo.VIAJES, new Color(255, 235, 205),
                () -> new PanelDespacho(usuarioActual), true, false));
        p.add(new Pestana("Pagos a conductores", "80 % de lo recaudado por cada viaje realizado", Modulo.PAGOS, new Color(255, 210, 170),
                () -> new PanelPagosConductores(usuarioActual), false, false));
        return p;
    }

    private void initComponents() {
        setTitle(objetosServicio.Marca.NOMBRE + " - Agencia de Viajes en Bus - " + usuarioActual.getNombre() + " (" + usuarioActual.getTipo() + ")");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 700);
        setExtendedState(MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        crearMenu();
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 14));
        tabbedPane.setOpaque(true);

        Set<Modulo> edita = Permisos.edita(usuarioActual);
        for (Pestana pestana : definirPestanas()) {
            if (!Permisos.puedeVer(usuarioActual, pestana.modulo)) {
                continue;
            }
            JPanel contenido = pestana.fabrica.get();
            boolean puedeEditar = edita.contains(pestana.modulo) && !pestana.soloLectura;
            tabbedPane.addTab(pestana.titulo, (Icon) null, envolver(contenido, pestana.sinAgregar, !puedeEditar), pestana.ayuda);
            tabbedPane.setBackgroundAt(tabbedPane.getTabCount() - 1, pestana.color);
        }
        Tema.pestanas(tabbedPane);

        if (usuarioActual.getEmpresa() != null) {
            try {
                Empresa completa = new Empresas("empresas.dat").obten(usuarioActual.getEmpresa());
                if (completa != null) {
                    usuarioActual.setEmpresa(completa);
                }
            } catch (Exception ignorado) {
                // se sigue con los datos básicos de la empresa
            }
            if ("Operador".equalsIgnoreCase(usuarioActual.getTipo())) {
                bloquearModulos();
            } else {
                desbloquearModulos(usuarioActual.getEmpresa());
            }
        } else if ("Administrador".equalsIgnoreCase(usuarioActual.getTipo())) {
            String cargo = usuarioActual.getCargo() == null || usuarioActual.getCargo().isEmpty() ? "ADMINISTRADOR" : usuarioActual.getCargo().toUpperCase();
            setTitle("Sistema de Transporte - Usuario: " + usuarioActual.getIdUsuario() + " | " + cargo);
            for (int i = 0; i < tabbedPane.getTabCount(); ++i) {
                tabbedPane.setEnabledAt(i, true);
            }
        } else {
            bloquearModulos();
        }

        List<JButton> extras = new ArrayList<>();
        if (Permisos.puedeVer(usuarioActual, Modulo.REPORTES)) {
            JButton informes = Tema.boton("Informes PDF", false);
            informes.addActionListener(e -> abrirInformes());
            extras.add(informes);
        }
        add(BarraSuperior.crear(this, usuarioActual, null, null, extras.toArray(new JButton[0])), BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        JPanel inferior = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inferior.setBorder(BorderFactory.createEtchedBorder());
        JLabel info = new JLabel("Sistema de Gestión de Buses v2.0 | Usuario: " + usuarioActual.getIdUsuario());
        info.setFont(new Font("Arial", Font.PLAIN, 11));
        inferior.add(info);
        add(inferior, BorderLayout.SOUTH);
    }

    private int indiceDe(String titulo) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(titulo)) {
                return i;
            }
        }
        return -1;
    }

    /** Operador sin empresa elegida: solo la pestaña Empresas está disponible. */
    public void bloquearModulos() {
        empresaActual = null;
        int empresas = indiceDe("Empresas");
        for (int i = 0; i < tabbedPane.getTabCount(); ++i) {
            if (i != empresas) {
                tabbedPane.setEnabledAt(i, false);
                tabbedPane.setBackgroundAt(i, Color.LIGHT_GRAY);
            }
        }
        if (empresas >= 0) {
            tabbedPane.setSelectedIndex(empresas);
        }
        setTitle("Sistema de Transporte - Usuario: " + usuarioActual.getIdUsuario() + " | Seleccione una Empresa");
    }

    public void desbloquearModulos(Empresa empresa) {
        empresaActual = empresa;
        if (panelBuses != null) panelBuses.setEmpresaFiltro(empresa);
        if (panelRutas != null) panelRutas.setEmpresaFiltro(empresa);
        if (panelViajes != null) panelViajes.setEmpresaFiltro(empresa);
        if (panelReservas != null) panelReservas.setEmpresaFiltro(empresa);
        if (panelPasajeros != null) panelPasajeros.setEmpresaFiltro(empresa);
        if (panelConductores != null) panelConductores.setEmpresaFiltro(empresa);
        for (Pestana p : definirColores()) {
            int i = indiceDe(p.titulo);
            if (i >= 0) {
                tabbedPane.setEnabledAt(i, true);
                tabbedPane.setBackgroundAt(i, p.color);
            }
        }
        String nombre = empresa.getNombre() != null ? empresa.getNombre() : empresa.getIdEmpresa();
        setTitle("Sistema de Transporte - Usuario: " + usuarioActual.getIdUsuario() + " | Empresa: " + nombre);
        int siguiente = Math.min(1, tabbedPane.getTabCount() - 1);
        tabbedPane.setSelectedIndex(siguiente);
        JLabel mensaje = new JLabel("Acceso concedido a: " + nombre);
        mensaje.setFont(new Font("Arial", Font.BOLD, 18));
        JOptionPane.showMessageDialog(this, mensaje, "Bienvenido", JOptionPane.INFORMATION_MESSAGE);
    }

    private List<Pestana> definirColores() {
        List<Pestana> lista = new ArrayList<>();
        for (Pestana p : definirPestanasSinFabricar()) {
            lista.add(p);
        }
        return lista;
    }

    private List<Pestana> definirPestanasSinFabricar() {
        List<Pestana> p = new ArrayList<>();
        String[] titulos = {"Empresas", "Usuarios", "Conductores", "Pasajeros", "Buses", "Rutas", "Viajes", "Reservas", "Facturas", "Novedades", "Recargas", "Flota y despacho", "Pagos a conductores"};
        Color[] colores = {new Color(220, 220, 220), new Color(230, 230, 250), new Color(255, 215, 0), new Color(255, 228, 196), new Color(173, 216, 230),
                new Color(152, 251, 152), new Color(255, 255, 224), new Color(255, 182, 193), new Color(224, 255, 255), new Color(255, 240, 200),
                new Color(200, 240, 200), new Color(255, 235, 205), new Color(255, 210, 170)};
        for (int i = 0; i < titulos.length; i++) {
            p.add(new Pestana(titulos[i], "", Modulo.EMPRESAS, colores[i], null, false, false));
        }
        return p;
    }

    // ------------------------------------------------------------------ menú y barra

    private void crearMenu() {
        menuBar = new JMenuBar();
        JMenu consultas = new JMenu("Consultas");
        consultas.setMnemonic('C');
        JMenuItem especiales = new JMenuItem("Consultas Especiales");
        especiales.setAccelerator(KeyStroke.getKeyStroke('K', java.awt.event.InputEvent.CTRL_DOWN_MASK));
        especiales.addActionListener(e -> new PantallaConsultasEspeciales(this, empresaActual).setVisible(true));
        consultas.add(especiales);
        JMenu reportes = new JMenu("Reportes");
        reportes.setMnemonic('P');
        if (Permisos.puedeVer(usuarioActual, Modulo.REPORTES)) {
            JMenuItem estadisticas = new JMenuItem("Reportes y Estadísticas");
            estadisticas.setAccelerator(KeyStroke.getKeyStroke('P', java.awt.event.InputEvent.CTRL_DOWN_MASK));
            estadisticas.addActionListener(e -> new PantallaReportes(this, empresaActual).setVisible(true));
            reportes.add(estadisticas);
            JMenuItem informes = new JMenuItem("Informes formales en PDF...");
            informes.addActionListener(e -> abrirInformes());
            reportes.add(informes);
            reportes.addSeparator();
        }
        JMenuItem pdf = new JMenuItem("Guardar la pestaña actual en PDF");
        pdf.addActionListener(e -> exportarPestana(ExportadorTabla.Formato.PDF));
        reportes.add(pdf);
        JMenuItem excel = new JMenuItem("Guardar la pestaña actual en Excel");
        excel.addActionListener(e -> exportarPestana(ExportadorTabla.Formato.EXCEL));
        reportes.add(excel);
        JMenu ayuda = new JMenu("Ayuda");
        ayuda.setMnemonic('A');
        JMenuItem manual = new JMenuItem("Manual de Usuario");
        manual.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        manual.addActionListener(e -> new PantallaManualUsuario(this).setVisible(true));
        ayuda.add(manual);
        JMenuItem acerca = new JMenuItem("Acerca de");
        acerca.addActionListener(e -> JOptionPane.showMessageDialog(this, "Sistema de Transporte Interprovincial\nVersión 2.0\n\nSistema integral de gestión de transporte de pasajeros\n"
                + "Buses, rutas, viajes, reservas, facturación y pagos a conductores.\n\n© 2026 - Todos los derechos reservados", "Acerca del Sistema", JOptionPane.INFORMATION_MESSAGE));
        ayuda.add(acerca);
        JMenu cuenta = new JMenu("Cuenta");
        cuenta.setMnemonic('N');
        JMenuItem perfil = new JMenuItem("Mi perfil");
        perfil.addActionListener(e -> new DialogoPerfil(this, usuarioActual, () -> { }).setVisible(true));
        cuenta.add(perfil);
        JMenuItem cerrar = new JMenuItem("Cerrar sesión");
        cerrar.addActionListener(e -> Navegador.cerrarSesion(this));
        cuenta.add(cerrar);
        menuBar.add(consultas);
        menuBar.add(reportes);
        menuBar.add(ayuda);
        menuBar.add(cuenta);
        setJMenuBar(menuBar);
    }

    private void abrirInformes() {
        new DialogoInformes(this, usuarioActual).setVisible(true);
    }

    /** Guarda lo que se ve en la tabla de la pestaña elegida (incluida la búsqueda aplicada). */
    private void exportarPestana(ExportadorTabla.Formato formato) {
        int i = tabbedPane.getSelectedIndex();
        if (i < 0) {
            return;
        }
        JTable tabla = ExportadorTabla.buscarTabla(tabbedPane.getComponentAt(i));
        ExportadorTabla.exportar(this, tabla, "Listado de " + tabbedPane.getTitleAt(i), formato);
    }

    // ------------------------------------------------------------------ pestañas

    private Component envolver(JPanel contenido, boolean sinAgregar, boolean soloConsulta) {
        JTable tabla = ExportadorTabla.buscarTabla(contenido);
        if (tabla == null) {
            return contenido;
        }
        Tema.encabezadoLegible(tabla);
        ocultarControles(contenido, sinAgregar, soloConsulta);
        BarraBusqueda barra = BarraBusqueda.para(tabla);
        if (barra == null) {
            return contenido;
        }
        JPanel envoltura = new JPanel(new BorderLayout());
        envoltura.setOpaque(false);
        envoltura.add(barra, BorderLayout.NORTH);
        envoltura.add(contenido, BorderLayout.CENTER);
        return envoltura;
    }

    /**
     * Quita los botones y cuadros "Buscar" de cada módulo (los reemplaza la barra general), el botón Agregar donde las cuentas
     * se crean solas y los botones que modifican datos donde el cargo solo puede consultar.
     */
    private void ocultarControles(Component c, boolean sinAgregar, boolean soloConsulta) {
        if (c instanceof JButton boton) {
            String texto = boton.getText() == null ? "" : boton.getText().trim().toLowerCase();
            boolean modifica = false;
            for (String clave : BOTONES_QUE_MODIFICAN) {
                modifica |= texto.startsWith(clave);
            }
            if (texto.equals("buscar") || (sinAgregar && texto.equals("agregar")) || (soloConsulta && modifica)) {
                boton.setVisible(false);
            }
        } else if (c instanceof JTextField campo) {
            Object ayuda = campo.getClientProperty("JTextField.placeholderText");
            if (ayuda != null && ayuda.toString().startsWith("Buscar")) {
                campo.setVisible(false);
            }
        } else if (c instanceof JLabel etiqueta && "\ud83d\udd0d".equals(etiqueta.getText())) {
            etiqueta.setVisible(false);
        }
        if (c instanceof Container contenedor) {
            for (Component hijo : contenedor.getComponents()) {
                ocultarControles(hijo, sinAgregar, soloConsulta);
            }
        }
    }
}
