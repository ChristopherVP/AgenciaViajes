package InterfacesUsuario;

import control.ControladorDespacho;
import control.ControladorSimulacion.Ubicacion;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import objetosNegocio.Conductor;
import objetosNegocio.Ruta;
import objetosNegocio.Usuario;

/** El administrador manda al conductor a un destino: el viaje sale desde donde el conductor estará libre. */
public class DialogoAsignarViaje extends JDialog {
    private final ControladorDespacho despacho = new ControladorDespacho();
    private final Usuario actor;
    private final Ubicacion ubicacion;
    private final Runnable alTerminar;
    private final JComboBox<String> cmbDestino = new JComboBox<>();
    private final List<Ruta> rutas = new ArrayList<>();
    private final JSpinner spnSalida;
    private final JLabel lblMensaje = Tema.mensaje();

    public DialogoAsignarViaje(Window padre, Usuario actor, Ubicacion ubicacion, Runnable alTerminar) {
        super(padre, "Asignar viaje", Dialog.ModalityType.APPLICATION_MODAL);
        this.actor = actor;
        this.ubicacion = ubicacion;
        this.alTerminar = alTerminar;
        setLayout(new BorderLayout());
        add(Tema.encabezado("Asignar viaje a " + ubicacion.nombre, "Sale desde " + ubicacion.proximoOrigen), BorderLayout.NORTH);

        cargarDestinos();
        LocalDateTime sugerida = ubicacion.disponibleDesde.isAfter(LocalDateTime.now().plusMinutes(20)) ? ubicacion.disponibleDesde : LocalDateTime.now().plusMinutes(20);
        sugerida = sugerida.withSecond(0).withNano(0).plusMinutes(5 - sugerida.getMinute() % 5);
        spnSalida = new JSpinner(new SpinnerDateModel(Date.from(sugerida.atZone(ZoneId.systemDefault()).toInstant()), null, null, java.util.Calendar.MINUTE));
        spnSalida.setEditor(new JSpinner.DateEditor(spnSalida, "dd/MM/yyyy HH:mm"));

        JPanel f = Tema.panelFormulario();
        Tema.fila(f, 0, "Ubicación del conductor", Tema.etiqueta(ubicacion.dondeEsta));
        Tema.fila(f, 1, "Saldrá desde", Tema.titulo(ubicacion.proximoOrigen, 14f));
        Tema.fila(f, 2, "Destino", cmbDestino);
        Tema.fila(f, 3, "Fecha y hora de salida", spnSalida);
        Tema.fila(f, 4, "Libre desde", Tema.etiqueta(ubicacion.disponibleDesde.format(control.ControladorLiquidaciones.FECHA)));

        JButton asignar = Tema.boton("Asignar viaje", true);
        asignar.addActionListener(e -> asignar());
        asignar.setEnabled(!rutas.isEmpty());
        JButton cancelar = Tema.boton("Cerrar", false);
        cancelar.addActionListener(e -> dispose());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(cancelar);
        botones.add(asignar);
        JLabel nota = Tema.nota(520, "<html>El conductor recibe un aviso y debe <b>aceptar</b> el viaje (necesita su tarjeta de cobro validada). "
                + "Volver al punto de partida es opcional: elige cualquier destino disponible.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);
        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        centro.add(f, BorderLayout.NORTH);
        centro.add(nota, BorderLayout.CENTER);
        JPanel sur = new JPanel(new BorderLayout(0, 6));
        sur.setOpaque(false);
        sur.add(lblMensaje, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.CENTER);
        centro.add(sur, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);
        setSize(new Dimension(600, 460));
        setResizable(false);
        setLocationRelativeTo(padre);
    }

    private void cargarDestinos() {
        try {
            Conductor c = new persistencia.Conductores("conductores.dat").obten(new Conductor(ubicacion.idConductor));
            if (c == null || c.getEmpresa() == null) {
                return;
            }
            for (Ruta r : despacho.rutasDesde(ubicacion.proximoOrigen, c.getEmpresa().getIdEmpresa())) {
                rutas.add(r);
                cmbDestino.addItem(r.getDestino() + " · " + (int) r.getDistancia() + " km · " + Tema.dinero(r.getPrecio()));
            }
        } catch (Exception e) {
            System.err.println("No se pudieron cargar los destinos: " + e.getMessage());
        }
        if (rutas.isEmpty()) {
            Tema.mostrarMensaje(lblMensaje, "No hay rutas activas que salgan de " + ubicacion.proximoOrigen + ". Crea una en la pestaña Rutas.", false);
        }
    }

    private void asignar() {
        int i = cmbDestino.getSelectedIndex();
        if (i < 0) {
            return;
        }
        LocalDateTime salida = ((Date) spnSalida.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        ResultadoOperacion r = despacho.asignarViaje(actor, ubicacion.idConductor, rutas.get(i).getIdRuta(), salida);
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        Tema.info(this, r.getMensaje());
        alTerminar.run();
        dispose();
    }
}
