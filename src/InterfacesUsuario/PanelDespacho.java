package InterfacesUsuario;

import control.ControladorDespacho;
import control.ControladorNotificaciones;
import control.ControladorSimulacion;
import control.ControladorSimulacion.Ubicacion;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Dimension;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import objetosNegocio.Usuario;
import objetosNegocio.Viaje;
import objetosServicio.Permisos;

/** Flota y despacho: dónde está cada conductor y desde dónde debe salir su próximo viaje, más los avisos de los conductores. */
public class PanelDespacho extends JPanel {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ControladorDespacho despacho = new ControladorDespacho();
    private final ControladorSimulacion simulacion = new ControladorSimulacion();
    private final ControladorNotificaciones notificaciones = new ControladorNotificaciones();
    private final Usuario usuario;
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"Conductor", "Bus", "Situación", "Dónde está", "Último destino", "Próximo origen", "Libre desde", "Por aceptar", "Programados"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private final DefaultTableModel modeloAvisos = new DefaultTableModel(new String[]{"Fecha", "Aviso", "Detalle"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JLabel lblMensaje = Tema.mensaje();
    private List<Ubicacion> flota = new ArrayList<>();

    public PanelDespacho(Usuario usuario) {
        this.usuario = usuario;
        setLayout(new BorderLayout(0, 8));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        Tema.tabla(tabla);
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        int[] anchos = {170, 90, 170, 280, 110, 110, 130, 80, 90};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        JButton asignar = Tema.boton("Asignar viaje desde su ubicación", true);
        asignar.setEnabled(Permisos.puedeEditar(usuario, Permisos.Modulo.VIAJES));
        asignar.addActionListener(e -> asignar());
        JButton mapa = Tema.boton("Ver bus en el mapa", false);
        mapa.addActionListener(e -> verMapa());
        JButton actualizar = Tema.boton("Actualizar", false);
        actualizar.addActionListener(e -> cargar());
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        barra.setOpaque(false);
        barra.add(asignar);
        barra.add(mapa);
        barra.add(actualizar);
        JLabel nota = Tema.nota(1000, "<html>Cada conductor sale desde donde <b>terminó su último viaje</b> (o donde terminará los que ya tiene). "
                + "El siguiente viaje se asigna desde ahí; volver al origen es opcional. La tabla se actualiza sola.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);
        JPanel arriba = new JPanel(new BorderLayout());
        arriba.setOpaque(false);
        arriba.add(barra, BorderLayout.NORTH);
        arriba.add(nota, BorderLayout.SOUTH);
        add(arriba, BorderLayout.NORTH);

        JTable tablaAvisos = new JTable(modeloAvisos);
        Tema.tabla(tablaAvisos);
        JScrollPane avisos = new JScrollPane(tablaAvisos);
        avisos.setBorder(BorderFactory.createTitledBorder("Avisos de conductores (aceptaciones, rechazos, solicitudes)"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tabla), avisos);
        split.setResizeWeight(0.65);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        add(lblMensaje, BorderLayout.SOUTH);
        cargar();
        Timer reloj = new Timer(5000, e -> {
            if (isShowing()) {
                refrescarSuave();
            }
        });
        reloj.start();
    }

    private void refrescarSuave() {
        int fila = tabla.getSelectedRow();
        cargar();
        if (fila >= 0 && fila < tabla.getRowCount()) {
            tabla.setRowSelectionInterval(fila, fila);
        }
    }

    @SuppressWarnings("unchecked")
    public final void cargar() {
        simulacion.avanzar();
        modelo.setRowCount(0);
        ResultadoOperacion r = despacho.flota(usuario);
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        flota = (List<Ubicacion>) r.getDatos();
        for (Ubicacion u : flota) {
            modelo.addRow(new Object[]{u.nombre, u.placaBus.isEmpty() ? "—" : u.placaBus, u.situacion, u.dondeEsta,
                    u.ultimoDestino.isEmpty() ? "—" : u.ultimoDestino, u.proximoOrigen.isEmpty() ? "—" : u.proximoOrigen,
                    u.disponibleDesde.format(FORMATO), u.porAceptar, u.programados});
        }
        modeloAvisos.setRowCount(0);
        for (ControladorNotificaciones.Aviso a : notificaciones.deDestino(ControladorNotificaciones.ADMIN)) {
            modeloAvisos.addRow(new Object[]{a.getFecha().format(FORMATO), a.getTitulo(), a.getMensaje()});
        }
        Tema.mostrarMensaje(lblMensaje, flota.size() + " conductor(es) activo(s).", true);
    }

    private Ubicacion seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= flota.size()) {
            Tema.info(this, "Selecciona un conductor de la lista.");
            return null;
        }
        return flota.get(fila);
    }

    private void asignar() {
        Ubicacion u = seleccionada();
        if (u == null) {
            return;
        }
        if (!u.tieneAsignacion || u.proximoOrigen.isEmpty()) {
            Tema.error(this, u.nombre + " no tiene bus asignado. Asígnale uno primero en la pestaña Conductores.");
            return;
        }
        new DialogoAsignarViaje(javax.swing.SwingUtilities.getWindowAncestor(this), usuario, u, this::cargar).setVisible(true);
    }

    private void verMapa() {
        Ubicacion u = seleccionada();
        if (u == null) {
            return;
        }
        if (u.idViajeActual.isEmpty()) {
            Tema.info(this, u.nombre + " no está en ruta ahora. " + u.dondeEsta);
            return;
        }
        try {
            Viaje v = fachadas.FachadaArchivos.getInstance().obten(new Viaje(u.idViajeActual));
            if (v != null) {
                new DialogoSeguimientoViaje(javax.swing.SwingUtilities.getWindowAncestor(this), v).setVisible(true);
            }
        } catch (Exception e) {
            Tema.error(this, "No se pudo abrir el seguimiento: " + e.getMessage());
        }
    }
}
