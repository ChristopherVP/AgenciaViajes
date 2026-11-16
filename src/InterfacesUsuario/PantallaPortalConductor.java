package InterfacesUsuario;

import control.ControladorNovedades;
import control.ControladorCompras;
import control.ControladorPortalConductor;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import objetosNegocio.Novedad;
import objetosNegocio.Usuario;
import objetosNegocio.Viaje;

/**
 * Portal del conductor: ve su bus y ruta asignados, los viajes que el administrador le programó, notifica cuando un viaje se realizó
 * con éxito (para que finanzas le pague el 80 %), gestiona su tarjeta de débito y puede enviar reportes al administrador.
 */
public class PantallaPortalConductor extends JFrame {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Usuario usuario;
    private final ControladorPortalConductor portal = new ControladorPortalConductor();
    private final ControladorNovedades novedades = new ControladorNovedades();
    private final ControladorCompras compras = new ControladorCompras();

    private final DefaultTableModel modeloViajes = new DefaultTableModel(new String[]{"Viaje", "Salida", "Ruta", "Bus", "Pasajeros", "Estado", "Mi función"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JCheckBox chkProximos = new JCheckBox("Ocultar viajes pagados y cancelados", true);
    private final JLabel lblAsignacion = new JLabel(" ");
    private final JTable tablaViajes = new JTable(modeloViajes);
    private PanelMisPagos panelPagos;
    private PanelFacturacion panelFacturas;
    private List<Viaje> viajes = new ArrayList<>();

    private final JComboBox<String> cmbTipo = new JComboBox<>();
    private final List<String> clavesTipo = new ArrayList<>(ControladorNovedades.TIPOS.keySet());
    private final JComboBox<String> cmbViaje = new JComboBox<>();
    private final List<String> idsViaje = new ArrayList<>();
    private final JTextField txtAsunto = Tema.campo(30);
    private final JTextArea txtMensaje = new JTextArea(6, 30);
    private final JLabel lblContador = Tema.etiqueta("0 / 300");
    private final JLabel lblMensaje = Tema.mensaje();

    private final DefaultTableModel modeloReportes = new DefaultTableModel(new String[]{"Fecha", "Tipo", "Asunto", "Estado"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tablaReportes = new JTable(modeloReportes);
    private final JTextArea detalle = new JTextArea();
    private List<Novedad> reportes = new ArrayList<>();

    public PantallaPortalConductor(Usuario usuario) {
        this.usuario = usuario;
        setTitle(objetosServicio.Marca.NOMBRE + " - Conductor: " + usuario.getNombre());
        setIconImage(IconoApp.crear(64));
        Navegador.confirmarAlSalir(this);
        setLayout(new BorderLayout());
        lblAsignacion.setFont(Tema.fuente(Font.BOLD, 14f));
        add(BarraSuperior.crear(this, usuario, lblAsignacion, null), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Tema.fuente(Font.BOLD, 14f));
        tabs.addTab("Mis viajes", crearPanelViajes());
        tabs.addTab("Avisos", crearPanelAvisos());
        tabs.addTab("Reportar al administrador", crearPanelReporte());
        tabs.addTab("Mis reportes", crearPanelReportes());
        panelPagos = new PanelMisPagos(usuario);
        tabs.addTab("Mis pagos", panelPagos);
        panelFacturas = new PanelFacturacion(usuario, false);
        tabs.addTab("Mis facturas", conBusqueda(panelFacturas, panelFacturas.getTabla()));
        Tema.pestanas(tabs);
        add(tabs, BorderLayout.CENTER);
        cargarViajes();
        cargarReportes();
        cargarAvisos();
        avisarNuevos();
        new javax.swing.Timer(5000, e -> {
            new control.ControladorSimulacion().avanzar();
            int fila = tablaViajes.getSelectedRow();
            cargarViajes();
            if (fila >= 0 && fila < tablaViajes.getRowCount()) {
                tablaViajes.setRowSelectionInterval(fila, fila);
            }
            cargarAvisos();
            avisarNuevos();
        }).start();
        setPreferredSize(new Dimension(1000, 680));
        pack();
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);
    }

    private static JPanel conBusqueda(JPanel panel, JTable tabla) {
        JPanel envoltura = new JPanel(new BorderLayout());
        envoltura.setOpaque(false);
        BarraBusqueda barra = BarraBusqueda.para(tabla);
        if (barra != null) {
            envoltura.add(barra, BorderLayout.NORTH);
        }
        envoltura.add(panel, BorderLayout.CENTER);
        return envoltura;
    }

    /** Muestra el bus y la ruta asignados, o el aviso de que aún no tiene. */
    private void actualizarAsignacion() {
        String texto = new control.ControladorAsignaciones().describir(usuario.getIdPerfil());
        boolean sin = texto.startsWith("Sin bus");
        lblAsignacion.setText(sin ? "Aún no tienes bus ni ruta asignados" : "Mi bus y ruta: " + texto);
        lblAsignacion.setForeground(sin ? Tema.ERROR : new Color(0x0B6B2E));
    }

    // ------------------------------------------------------------------ mis viajes

    private JPanel crearPanelViajes() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        Tema.tabla(tablaViajes);
        JScrollPane scroll = new JScrollPane(tablaViajes);
        scroll.getViewport().setBackground(Color.WHITE);
        p.add(scroll, BorderLayout.CENTER);
        chkProximos.setOpaque(false);
        chkProximos.addActionListener(e -> cargarViajes());
        javax.swing.JButton actualizar = Tema.boton("Actualizar", false);
        actualizar.addActionListener(e -> cargarViajes());
        javax.swing.JButton aceptar = Tema.boton("Aceptar viaje", true);
        aceptar.addActionListener(e -> aceptarViaje());
        javax.swing.JButton rechazar = Tema.boton("Rechazar", false);
        rechazar.addActionListener(e -> rechazarViaje());
        javax.swing.JButton vivo = Tema.boton("Seguimiento en vivo", false);
        vivo.addActionListener(e -> seguimiento());
        javax.swing.JButton verRuta = Tema.boton("Ver ruta en el mapa", false);
        verRuta.addActionListener(e -> verRuta());
        JPanel arriba = new JPanel(new BorderLayout(0, 4));
        arriba.setOpaque(false);
        JPanel botonesViaje = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        botonesViaje.setOpaque(false);
        botonesViaje.add(aceptar);
        botonesViaje.add(rechazar);
        botonesViaje.add(vivo);
        botonesViaje.add(verRuta);
        botonesViaje.add(chkProximos);
        botonesViaje.add(actualizar);
        JLabel nota = Tema.etiqueta("Acepta los viajes que te asigna el administrador (necesitas una tarjeta de cobro validada). Tu pago del 80 % se libera al llegar el bus a destino.");
        nota.setForeground(Tema.TEXTO_SUAVE);
        arriba.add(botonesViaje, BorderLayout.NORTH);
        arriba.add(nota, BorderLayout.SOUTH);
        p.add(arriba, BorderLayout.NORTH);
        return p;
    }

    private void cargarViajes() {
        actualizarAsignacion();
        modeloViajes.setRowCount(0);
        ResultadoOperacion r = portal.viajesAsignados(usuario);
        if (!r.isExito()) {
            Tema.error(this, r.getMensaje());
            return;
        }
        @SuppressWarnings("unchecked")
        List<Viaje> todos = (List<Viaje>) r.getDatos();
        todos = new ArrayList<>(todos);
        java.util.Collections.reverse(todos);
        viajes = new ArrayList<>();
        idsViaje.clear();
        cmbViaje.removeAllItems();
        cmbViaje.addItem("(Sin viaje específico)");
        idsViaje.add("");
        for (Viaje v : todos) {
            LocalDateTime salida = ControladorCompras.salidaDe(v);
            boolean proximo = salida != null && salida.isAfter(LocalDateTime.now().minusHours(6));
            boolean cancelado = v.getEstado() != null && v.getEstado().toLowerCase().startsWith("cancel");
            boolean completado = v.getEstado() != null && v.getEstado().equalsIgnoreCase("Completado");
            if (chkProximos.isSelected() && (cancelado || completado)) {
                continue;
            }
            viajes.add(v);
            int ocupados = v.getBus() != null ? Math.max(0, v.getBus().getCapacidad() - v.getAsientosDisponibles()) : 0;
            String funcion = v.getConductor1() != null && usuario.getIdPerfil().equals(v.getConductor1().getIdConductor()) ? "Principal" : "Auxiliar";
            String ruta = compras.describirRuta(v);
            modeloViajes.addRow(new Object[]{v.getIdViaje(), salida == null ? "" : salida.format(FORMATO), ruta,
                    v.getBus() != null ? v.getBus().getPlaca() : "", ocupados + (v.getBus() != null ? " / " + v.getBus().getCapacidad() : ""), v.getEstado(), funcion});
            if (proximo && !cancelado) {
                cmbViaje.addItem(v.getIdViaje() + " · " + ruta + " · " + (salida == null ? "" : salida.format(FORMATO)));
                idsViaje.add(v.getIdViaje());
            }
        }
    }

    private String viajeSeleccionado() {
        int fila = tablaViajes.getSelectedRow();
        if (fila < 0) {
            Tema.error(this, "Selecciona un viaje de la lista.");
            return null;
        }
        return String.valueOf(modeloViajes.getValueAt(fila, 0));
    }

    private Viaje viajeElegido() {
        String id = viajeSeleccionado();
        return id == null ? null : viajes.stream().filter(v -> v.getIdViaje().equals(id)).findFirst().orElse(null);
    }

    private void aceptarViaje() {
        Viaje v = viajeElegido();
        if (v == null) {
            return;
        }
        String errorTarjeta = control.ControladorDespacho.errorTarjeta(usuario.getIdPerfil());
        if (errorTarjeta != null) {
            Object[] op = {"Registrar tarjeta ahora", "Cerrar"};
            if (javax.swing.JOptionPane.showOptionDialog(this, errorTarjeta + "\n\nSin una tarjeta de cobro validada no puedes aceptar viajes.",
                    "Falta tu tarjeta de cobro", javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE, null, op, op[0]) == 0) {
                new DialogoTarjetaCobro(this, usuario, this::actualizarTodo).setVisible(true);
            }
            return;
        }
        ResultadoOperacion r = new control.ControladorDespacho().aceptar(usuario, v.getIdViaje());
        if (r.isExito()) {
            Tema.info(this, r.getMensaje());
        } else {
            Tema.error(this, r.getMensaje());
        }
        actualizarTodo();
    }

    private void rechazarViaje() {
        Viaje v = viajeElegido();
        if (v == null) {
            return;
        }
        if (javax.swing.JOptionPane.showConfirmDialog(this, "¿Rechazar el viaje " + v.getIdViaje() + "? Volverá al administrador para reasignarlo.",
                "Rechazar viaje", javax.swing.JOptionPane.YES_NO_OPTION) != javax.swing.JOptionPane.YES_OPTION) {
            return;
        }
        ResultadoOperacion r = new control.ControladorDespacho().rechazar(usuario, v.getIdViaje());
        if (r.isExito()) Tema.info(this, r.getMensaje()); else Tema.error(this, r.getMensaje());
        actualizarTodo();
    }

    private void seguimiento() {
        Viaje v = viajeElegido();
        if (v != null) {
            new DialogoSeguimientoViaje(this, v).setVisible(true);
        }
    }

    private void actualizarTodo() {
        cargarViajes();
        cargarReportes();
        cargarAvisos();
        panelPagos.cargar();
    }

    private void verRuta() {
        String id = viajeSeleccionado();
        if (id == null) {
            return;
        }
        for (Viaje v : viajes) {
            if (v.getIdViaje().equals(id)) {
                try {
                    objetosNegocio.Ruta ruta = fachadas.FachadaArchivos.getInstance().obten(v.getRuta());
                    if (ruta != null) {
                        new DialogoMapaRuta(this, ruta, "").setVisible(true);
                    }
                } catch (Exception e) {
                    Tema.error(this, "No se pudo cargar la ruta: " + e.getMessage());
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------ reportar

    private JPanel crearPanelReporte() {
        for (String clave : clavesTipo) {
            cmbTipo.addItem(ControladorNovedades.TIPOS.get(clave));
        }
        txtMensaje.setLineWrap(true);
        txtMensaje.setWrapStyleWord(true);
        txtMensaje.setFont(Tema.fuente(Font.PLAIN, 14f));
        txtMensaje.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        txtMensaje.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void c() {
                int n = txtMensaje.getText().length();
                lblContador.setText(n + " / 300");
                lblContador.setForeground(n > 300 ? Tema.ERROR : Tema.TEXTO_SUAVE);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { c(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { c(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { c(); }
        });
        JScrollPane scrollMensaje = new JScrollPane(txtMensaje);
        scrollMensaje.setBorder(BorderFactory.createLineBorder(Tema.BORDE));

        JPanel f = Tema.panelFormulario();
        Tema.fila(f, 0, "Tipo de reporte", cmbTipo);
        Tema.fila(f, 1, "Viaje relacionado", cmbViaje);
        Tema.fila(f, 2, "Asunto", txtAsunto);
        Tema.fila(f, 3, "Mensaje", scrollMensaje);
        Tema.fila(f, 4, "", lblContador);

        javax.swing.JButton enviar = Tema.boton("Enviar al administrador", true);
        enviar.addActionListener(e -> enviarReporte());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(enviar);

        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(18, 40, 18, 40));
        JLabel nota = Tema.nota(740, "<html>Usa este formulario si <b>no puedes realizar un viaje</b> o el bus tiene una <b>falla</b> (elige el viaje: solo se puede avisar una vez por viaje y antes de que salga), o para una queja. El administrador recibe el reporte al instante y te responde aquí.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);
        p.add(nota, BorderLayout.NORTH);
        p.add(f, BorderLayout.CENTER);
        JPanel sur = new JPanel(new BorderLayout(0, 6));
        sur.setOpaque(false);
        sur.add(lblMensaje, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.CENTER);
        p.add(sur, BorderLayout.SOUTH);
        return p;
    }

    private void enviarReporte() {
        int tipo = cmbTipo.getSelectedIndex();
        int viaje = Math.max(0, cmbViaje.getSelectedIndex());
        ResultadoOperacion r = novedades.reportar(usuario, idsViaje.get(Math.min(viaje, idsViaje.size() - 1)), clavesTipo.get(tipo), txtAsunto.getText(), txtMensaje.getText());
        Tema.mostrarMensaje(lblMensaje, r.getMensaje(), r.isExito());
        if (!r.isExito()) {
            return;
        }
        Novedad enviada = (Novedad) r.getDatos();
        // el formulario queda limpio para el siguiente reporte
        txtAsunto.setText("");
        txtMensaje.setText("");
        cmbTipo.setSelectedIndex(0);
        cmbViaje.setSelectedIndex(0);
        cargarReportes();
        Object[] op = {"Descargar PDF", "Cerrar"};
        if (javax.swing.JOptionPane.showOptionDialog(this, "Tu reporte " + enviada.getIdNovedad() + " fue enviado al administrador.\n¿Quieres descargar su PDF?",
                "Reporte enviado", javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.INFORMATION_MESSAGE, null, op, op[0]) == 0) {
            descargarReporte(enviada);
        }
    }

    // ------------------------------------------------------------------ mis reportes

    private JPanel crearPanelReportes() {
        Tema.tabla(tablaReportes);
        tablaReportes.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tablaReportes.getSelectionModel().addListSelectionListener(e -> {
            int fila = tablaReportes.getSelectedRow();
            if (e.getValueIsAdjusting() || fila < 0 || fila >= reportes.size()) {
                return;
            }
            Novedad n = reportes.get(fila);
            detalle.setText("Tu mensaje:\n" + n.getMensaje() + "\n\n"
                    + (n.getRespuesta() == null || n.getRespuesta().isEmpty() ? "El administrador aún no responde." : "Respuesta del administrador:\n" + n.getRespuesta()));
            detalle.setCaretPosition(0);
        });
        detalle.setEditable(false);
        detalle.setLineWrap(true);
        detalle.setWrapStyleWord(true);
        detalle.setFont(Tema.fuente(Font.PLAIN, 14f));
        detalle.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tablaReportes), new JScrollPane(detalle));
        split.setResizeWeight(0.55);
        split.setBorder(null);
        javax.swing.JButton actualizar = Tema.boton("Actualizar", false);
        actualizar.addActionListener(e -> cargarReportes());
        javax.swing.JButton pdf = Tema.boton("Descargar PDF del reporte", false);
        pdf.addActionListener(e -> {
            int fila = tablaReportes.getSelectedRow();
            if (fila < 0 || fila >= reportes.size()) {
                Tema.info(this, "Selecciona un reporte de la lista.");
                return;
            }
            descargarReporte(reportes.get(fila));
        });
        JPanel arriba = new JPanel(new FlowLayout(FlowLayout.LEFT));
        arriba.setOpaque(false);
        arriba.add(pdf);
        arriba.add(actualizar);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        p.add(arriba, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    private void descargarReporte(Novedad n) {
        Descargas.guardarPdf(this, "Descargar reporte", control.ComprobantesPdf.nombreSugerido("Reporte", n.getIdNovedad()),
                destino -> new control.ComprobantesPdf().novedad(destino, n));
    }

    @SuppressWarnings("unchecked")
    private void cargarReportes() {
        modeloReportes.setRowCount(0);
        detalle.setText("");
        ResultadoOperacion r = novedades.listarDeConductor(usuario);
        if (!r.isExito()) {
            return;
        }
        reportes = (List<Novedad>) r.getDatos();
        for (Novedad n : reportes) {
            String fecha = n.getFecha() == null ? "" : String.format("%02d/%02d/%04d", n.getFecha().getDia(), n.getFecha().getMes(), n.getFecha().getAnio());
            modeloReportes.addRow(new Object[]{fecha, ControladorNovedades.TIPOS.getOrDefault(n.getTipo(), n.getTipo()), n.getAsunto(), n.getEstado()});
        }
    }

    // ------------------------------------------------------------------ avisos

    private final control.ControladorNotificaciones notificaciones = new control.ControladorNotificaciones();
    private final DefaultTableModel modeloAvisos = new DefaultTableModel(new String[]{"Fecha", "Aviso", "Detalle"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private boolean mostrandoAviso;

    private JPanel crearPanelAvisos() {
        JTable tabla = new JTable(modeloAvisos);
        Tema.tabla(tabla);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(600);
        javax.swing.JButton actualizar = Tema.boton("Actualizar", false);
        actualizar.addActionListener(e -> cargarAvisos());
        JPanel arriba = new JPanel(new FlowLayout(FlowLayout.LEFT));
        arriba.setOpaque(false);
        arriba.add(actualizar);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        p.add(arriba, BorderLayout.NORTH);
        p.add(new JScrollPane(tabla), BorderLayout.CENTER);
        return p;
    }

    private void cargarAvisos() {
        modeloAvisos.setRowCount(0);
        for (control.ControladorNotificaciones.Aviso a : notificaciones.deDestino(usuario.getIdPerfil())) {
            modeloAvisos.addRow(new Object[]{a.getFecha().format(FORMATO), (a.isLeido() ? "" : "● ") + a.getTitulo(), a.getMensaje()});
        }
    }

    /** Muestra una ventana con los avisos nuevos (viaje asignado, pagos, respuestas) y los marca como leídos. */
    private void avisarNuevos() {
        if (mostrandoAviso) {
            return;
        }
        List<control.ControladorNotificaciones.Aviso> nuevos = notificaciones.sinLeer(usuario.getIdPerfil());
        if (nuevos.isEmpty()) {
            return;
        }
        mostrandoAviso = true;
        StringBuilder sb = new StringBuilder();
        for (control.ControladorNotificaciones.Aviso a : nuevos) {
            sb.append("• ").append(a.getTitulo()).append("\n  ").append(a.getMensaje()).append("\n\n");
        }
        notificaciones.marcarLeidos(usuario.getIdPerfil());
        javax.swing.SwingUtilities.invokeLater(() -> {
            javax.swing.JOptionPane.showMessageDialog(this, sb.toString().trim(), "Tienes " + nuevos.size() + " aviso(s) nuevo(s)", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            mostrandoAviso = false;
            cargarAvisos();
        });
    }
}
