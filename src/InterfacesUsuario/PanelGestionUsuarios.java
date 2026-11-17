package InterfacesUsuario;

import control.ControladorUsuarios;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import objetosNegocio.Conductor;
import objetosNegocio.Empresa;
import objetosNegocio.Pasajero;
import objetosNegocio.Rol;
import objetosNegocio.Usuario;
import persistencia.Conductores;
import persistencia.Empresas;
import persistencia.Pasajeros;
import persistencia.Usuarios;

/**
 * Gestión de cuentas: administradores/operadores, pasajeros y conductores en una sola ventana, con botones para ver
 * Todos, solo Administradores, solo Pasajeros o solo Conductores. Permite crear administradores, editar, eliminar y
 * aprobar las cuentas de conductores pendientes. Los pasajeros y conductores que tienen ficha pero aún no tienen
 * cuenta también aparecen (estado "SIN CUENTA"); se gestionan en sus pestañas.
 */
public class PanelGestionUsuarios extends JPanel {
    private static final String SIN_CUENTA = "SIN CUENTA";

    private final ControladorUsuarios controlador = new ControladorUsuarios();
    private final Usuario usuarioActual;
    private final String tipoFiltro;
    private final JLabel lblResumen = Tema.etiqueta(" ");
    private final DefaultTableModel modelo = new DefaultTableModel(new String[]{"ID", "Usuario", "Nombre", "Cédula", "Correo", "Rol", "Cargo", "Empresa", "Estado"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private final Map<String, Predicate<Usuario>> filtros = new LinkedHashMap<>();
    private final Set<String> fichasSinCuenta = new HashSet<>();
    private Predicate<Usuario> filtroActual = u -> true;
    private List<Usuario> filas = new ArrayList<>();

    public PanelGestionUsuarios(Usuario usuario, String tipoFiltro) {
        this.usuarioActual = usuario;
        this.tipoFiltro = tipoFiltro;
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        setBackground(Tema.FONDO);

        filtros.put("Todos", u -> true);
        filtros.put("Administradores", u -> u.getRol() != null && u.getRol().esAdministrativo());
        filtros.put("Pasajeros", u -> u.getRol() == Rol.PASAJERO);
        filtros.put("Conductores", u -> u.getRol() == Rol.CONDUCTOR);

        boolean esAdmin = usuario != null && usuario.getRol() == Rol.ADMINISTRADOR;
        JPanel arriba = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        arriba.setOpaque(false);
        javax.swing.JButton agregar = Tema.boton("Nuevo administrador", true);
        javax.swing.JButton editar = Tema.boton("Editar", false);
        javax.swing.JButton eliminar = Tema.boton("Eliminar", false);
        javax.swing.JButton aprobar = Tema.boton("Revisar solicitud", false);
        javax.swing.JButton actualizar = Tema.boton("Actualizar", false);
        agregar.addActionListener(e -> nuevo());
        editar.addActionListener(e -> editar());
        eliminar.addActionListener(e -> eliminar());
        aprobar.addActionListener(e -> aprobar());
        actualizar.addActionListener(e -> cargarDatos());
        for (javax.swing.JButton b : new javax.swing.JButton[]{editar, eliminar, aprobar}) {
            b.setEnabled(esAdmin);
        }
        // solo el administrador principal crea administradores; pasajeros y conductores se registran solos
        agregar.setEnabled(esAdmin && usuario.esAdministradorPrincipal());
        arriba.add(agregar);
        arriba.add(editar);
        arriba.add(eliminar);
        arriba.add(aprobar);
        arriba.add(actualizar);

        JPanel botonesFiltro = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        botonesFiltro.setOpaque(false);
        botonesFiltro.add(Tema.etiqueta("Mostrar:"));
        ButtonGroup grupo = new ButtonGroup();
        for (Map.Entry<String, Predicate<Usuario>> f : filtros.entrySet()) {
            JToggleButton b = new JToggleButton(f.getKey(), "Todos".equals(f.getKey()));
            b.setFont(Tema.fuente(Font.BOLD, 13f));
            b.setFocusPainted(false);
            b.addActionListener(e -> {
                filtroActual = f.getValue();
                cargarDatos();
            });
            grupo.add(b);
            botonesFiltro.add(b);
        }
        JPanel norte = new JPanel(new java.awt.GridLayout(2, 1, 0, 8));
        norte.setOpaque(false);
        norte.add(arriba);
        norte.add(botonesFiltro);
        add(norte, BorderLayout.NORTH);

        Tema.tabla(tabla);
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tabla.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foco, int fila, int col) {
                Component c = super.getTableCellRendererComponent(tb, v, sel, foco, fila, col);
                String s = String.valueOf(v);
                c.setForeground("ACTIVO".equals(s) ? Tema.EXITO : ("PENDIENTE".equals(s) ? new Color(0xB9770E) : (SIN_CUENTA.equals(s) ? Tema.TEXTO_SUAVE : Tema.ERROR)));
                c.setFont(Tema.fuente(Font.BOLD, 13f));
                return c;
            }
        });
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.getViewport().setBackground(Color.WHITE);
        add(scroll, BorderLayout.CENTER);
        add(lblResumen, BorderLayout.SOUTH);
        cargarDatos();
    }

    public PanelGestionUsuarios(Usuario usuario) {
        this(usuario, null);
    }

    public final void cargarDatos() {
        modelo.setRowCount(0);
        filas = new ArrayList<>();
        fichasSinCuenta.clear();
        try {
            Map<String, String> empresas = new HashMap<>();
            for (Empresa e : new Empresas("empresas.dat").lista()) {
                empresas.put(e.getIdEmpresa(), e.getNombre());
            }
            List<Usuario> todos = reunirUsuarios();
            int pendientes = 0;
            int admins = 0;
            int pasajeros = 0;
            int conductores = 0;
            for (Usuario u : todos) {
                if (tipoFiltro != null && !tipoFiltro.equalsIgnoreCase(u.getTipo())) {
                    continue;
                }
                if (u.getRol() != null) {
                    if (u.getRol().esAdministrativo()) admins++;
                    else if (u.getRol() == Rol.PASAJERO) pasajeros++;
                    else if (u.getRol() == Rol.CONDUCTOR) conductores++;
                }
                if (!filtroActual.test(u)) {
                    continue;
                }
                filas.add(u);
                String empresa = u.tieneEmpresa() ? empresas.getOrDefault(u.getEmpresa().getIdEmpresa(), u.getEmpresa().getIdEmpresa()) : "(plataforma)";
                modelo.addRow(new Object[]{u.getIdUsuario(), u.getLogin(), u.getNombre(), u.getCedula(), u.getEmail(), u.getTipo(),
                        u.getCargo() == null || u.getCargo().isEmpty() ? "—" : u.getCargo(), empresa, u.getEstado()});
                if (Usuario.ESTADO_PENDIENTE.equals(u.getEstado())) {
                    pendientes++;
                }
            }
            lblResumen.setText(filas.size() + " mostrado(s)  ·  " + admins + " administrador(es)  ·  " + pasajeros + " pasajero(s)  ·  "
                    + conductores + " conductor(es)" + (pendientes > 0 ? "  ·  " + pendientes + " cuenta(s) pendiente(s) de aprobación" : ""));
        } catch (Exception e) {
            Tema.error(this, "No se pudieron cargar los usuarios: " + e.getMessage());
        }
    }

    /**
     * Cuentas visibles para el administrador, más todos los pasajeros (son de toda la plataforma) y las fichas de
     * pasajeros y conductores que aún no tienen cuenta.
     */
    private List<Usuario> reunirUsuarios() throws Exception {
        List<Usuario> resultado = new ArrayList<>(controlador.listarVisibles(usuarioActual));
        if (usuarioActual == null || usuarioActual.getRol() != Rol.ADMINISTRADOR) {
            return resultado;
        }
        Set<String> ids = new HashSet<>();
        Set<String> perfiles = new HashSet<>();
        for (Usuario u : resultado) {
            ids.add(u.getIdUsuario());
        }
        for (Usuario u : new Usuarios("usuarios.dat").listaPorTipo(Rol.PASAJERO.getEtiqueta())) {
            if (ids.add(u.getIdUsuario())) {
                resultado.add(u);
            }
        }
        for (Usuario u : resultado) {
            if (u.getIdPerfil() != null && !u.getIdPerfil().isEmpty()) {
                perfiles.add(u.getIdPerfil());
            }
        }
        for (Pasajero p : new Pasajeros("pasajeros.dat").lista()) {
            if (!perfiles.contains(p.getIdPasajero())) {
                resultado.add(fichaComoUsuario(p.getIdPasajero(), p.getNombre(), p.getCedula(), p.getEmail(), Rol.PASAJERO, null));
            }
        }
        for (Conductor c : new Conductores("conductores.dat").lista()) {
            boolean propia = usuarioActual.esSuperAdministrador()
                    || (c.getEmpresa() != null && usuarioActual.tieneEmpresa()
                    && c.getEmpresa().getIdEmpresa().equals(usuarioActual.getEmpresa().getIdEmpresa()));
            if (propia && !perfiles.contains(c.getIdConductor())) {
                resultado.add(fichaComoUsuario(c.getIdConductor(), c.getNombre(), c.getCedula(), "", Rol.CONDUCTOR, c.getEmpresa()));
            }
        }
        return resultado;
    }

    private Usuario fichaComoUsuario(String idFicha, String nombre, String cedula, String email, Rol rol, Empresa empresa) {
        Usuario u = new Usuario(idFicha);
        u.setLogin("—");
        u.setNombre(nombre);
        u.setCedula(cedula);
        u.setEmail(email == null ? "" : email);
        u.setTipo(rol.getEtiqueta());
        u.setEstado(SIN_CUENTA);
        u.setEmpresa(empresa);
        u.setCargo("");
        fichasSinCuenta.add(idFicha);
        return u;
    }

    private List<Empresa> empresas() {
        try {
            return new Empresas("empresas.dat").lista();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Usuario seleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            Tema.error(this, "Selecciona un usuario de la lista.");
            return null;
        }
        String id = String.valueOf(modelo.getValueAt(fila, 0));
        if (fichasSinCuenta.contains(id)) {
            Tema.error(this, "Esta persona todavía no tiene cuenta de acceso. Su ficha se gestiona en la pestaña de Pasajeros o Conductores.");
            return null;
        }
        for (Usuario u : filas) {
            if (u.getIdUsuario().equals(id)) {
                return u;
            }
        }
        return null;
    }

    private void nuevo() {
        new DialogoFormularioUsuario(javax.swing.SwingUtilities.getWindowAncestor(this), usuarioActual, null, empresas(), this::cargarDatos).setVisible(true);
    }

    private void editar() {
        Usuario u = seleccionado();
        if (u != null) {
            new DialogoFormularioUsuario(javax.swing.SwingUtilities.getWindowAncestor(this), usuarioActual, u, empresas(), this::cargarDatos).setVisible(true);
        }
    }

    private void eliminar() {
        Usuario u = seleccionado();
        if (u == null) return;
        if (JOptionPane.showConfirmDialog(this, "¿Eliminar a " + u.getNombre() + " (" + u.getLogin() + ")?\nEsta acción no se puede deshacer.", "Confirmar eliminación", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        ResultadoOperacion r = controlador.eliminarUsuario(usuarioActual, u.getIdUsuario());
        if (!r.isExito()) Tema.error(this, r.getMensaje());
        cargarDatos();
    }

    private void aprobar() {
        Usuario u = seleccionado();
        if (u == null) return;
        if (!Usuario.ESTADO_PENDIENTE.equalsIgnoreCase(u.getEstado())) {
            Tema.error(this, "Solo se revisan solicitudes en estado PENDIENTE. Selecciona una cuenta pendiente.");
            return;
        }
        if (u.getRol() == objetosNegocio.Rol.CONDUCTOR) {
            new DialogoRevisionConductor(javax.swing.SwingUtilities.getWindowAncestor(this), usuarioActual, u, this::cargarDatos).setVisible(true);
            return;
        }
        ResultadoOperacion r = controlador.aprobarUsuario(usuarioActual, u.getIdUsuario());
        if (!r.isExito()) Tema.error(this, r.getMensaje()); else Tema.info(this, r.getMensaje());
        cargarDatos();
    }
}