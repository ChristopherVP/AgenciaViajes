package InterfacesUsuario;

import control.ControladorAsignaciones;
import control.ResultadoOperacion;
import fachadas.FachadaArchivos;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import objetosNegocio.Bus;
import objetosNegocio.Conductor;
import objetosNegocio.Ruta;
import objetosNegocio.Usuario;
import persistencia.Buses;
import persistencia.Rutas;

/**
 * Asigna a un conductor aprobado el bus que va a manejar y la ruta que va a recorrer. Mientras no tenga asignación no se le
 * puede programar ningún viaje. Cada bus se asigna a un solo conductor.
 */
public class DialogoAsignacionConductor extends JDialog {
    private final ControladorAsignaciones controlador = new ControladorAsignaciones();
    private final Usuario actor;
    private final Conductor conductor;
    private final Runnable alGuardar;
    private final JComboBox<String> cmbBus = new JComboBox<>();
    private final JComboBox<String> cmbRuta = new JComboBox<>();
    private final List<Bus> buses = new ArrayList<>();
    private final List<Ruta> rutas = new ArrayList<>();
    private final JLabel lblMensaje = Tema.mensaje();

    public DialogoAsignacionConductor(Window padre, Usuario actor, Conductor conductor, Runnable alGuardar) {
        super(padre, "Asignar bus y ruta", Dialog.ModalityType.APPLICATION_MODAL);
        this.actor = actor;
        this.conductor = conductor;
        this.alGuardar = alGuardar;
        setIconImage(IconoApp.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado("Asignar bus y ruta", conductor.getNombre()), BorderLayout.NORTH);

        String empresa = conductor.getEmpresa() == null ? "" : conductor.getEmpresa().getIdEmpresa();
        ControladorAsignaciones.Asignacion actual = controlador.obtener(conductor.getIdConductor());
        cmbBus.addItem("Selecciona un bus...");
        cmbRuta.addItem("Selecciona una ruta...");
        try {
            List<ControladorAsignaciones.Asignacion> todas = controlador.listar();
            for (Bus b : new Buses("buses.dat").lista()) {
                boolean mia = b.getEmpresa() != null && empresa.equals(b.getEmpresa().getIdEmpresa());
                boolean ocupado = todas.stream().anyMatch(a -> a.getIdBus().equals(b.getIdBus()) && !a.getIdConductor().equals(conductor.getIdConductor()));
                if (mia && "ACTIVO".equalsIgnoreCase(b.getEstado()) && !ocupado) {
                    buses.add(b);
                    cmbBus.addItem(b.getPlaca() + " · " + b.getMarca() + " · " + b.getCapacidad() + " asientos");
                    if (actual != null && actual.getIdBus().equals(b.getIdBus())) {
                        cmbBus.setSelectedIndex(buses.size());
                    }
                }
            }
            for (Ruta r : new Rutas("rutas.dat").lista()) {
                if (r.getEmpresa() != null && empresa.equals(r.getEmpresa().getIdEmpresa()) && !"INACTIVA".equalsIgnoreCase(r.getEstado())) {
                    rutas.add(r);
                    cmbRuta.addItem(r.getOrigen() + " → " + r.getDestino() + " · " + String.format("%.0f km · $%.2f", r.getDistancia(), r.getPrecio()));
                    if (actual != null && actual.getIdRuta().equals(r.getIdRuta())) {
                        cmbRuta.setSelectedIndex(rutas.size());
                    }
                }
            }
        } catch (Exception e) {
            Tema.mostrarMensaje(lblMensaje, "No se pudieron cargar los buses y rutas: " + e.getMessage(), false);
        }
        cmbBus.setFont(Tema.fuente(Font.PLAIN, 13f));
        cmbRuta.setFont(Tema.fuente(Font.PLAIN, 13f));

        JPanel f = Tema.panelFormulario();
        Tema.fila(f, 0, "Bus *", cmbBus);
        Tema.fila(f, 1, "Ruta *", cmbRuta);
        JLabel nota = Tema.nota(480, "<html>Solo aparecen los buses activos y sin conductor, y las rutas de la misma empresa del conductor. "
                + "Con esta asignación ya se le podrán programar viajes; el viaje debe usar el mismo bus y la misma ruta.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);

        JButton guardar = Tema.boton("Guardar asignación", true);
        guardar.addActionListener(e -> guardar());
        JButton cancelar = Tema.boton("Cancelar", false);
        cancelar.addActionListener(e -> dispose());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(cancelar);
        botones.add(guardar);
        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(BorderFactory.createEmptyBorder(16, 28, 16, 28));
        centro.add(nota, BorderLayout.NORTH);
        centro.add(f, BorderLayout.CENTER);
        JPanel sur = new JPanel(new BorderLayout(0, 6));
        sur.setOpaque(false);
        sur.add(lblMensaje, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.CENTER);
        centro.add(sur, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);
        setPreferredSize(new Dimension(640, 420));
        pack();
        setLocationRelativeTo(padre);
    }

    private void guardar() {
        if (cmbBus.getSelectedIndex() <= 0 || cmbRuta.getSelectedIndex() <= 0) {
            Tema.mostrarMensaje(lblMensaje, "Elige el bus y la ruta del conductor.", false);
            return;
        }
        ResultadoOperacion r = controlador.asignar(actor, conductor.getIdConductor(), buses.get(cmbBus.getSelectedIndex() - 1).getIdBus(),
                rutas.get(cmbRuta.getSelectedIndex() - 1).getIdRuta());
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        Tema.info(this, r.getMensaje());
        alGuardar.run();
        dispose();
    }
}
