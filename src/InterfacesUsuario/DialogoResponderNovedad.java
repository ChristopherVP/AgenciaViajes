package InterfacesUsuario;

import control.ControladorDespacho;
import control.ControladorNovedades;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import objetosNegocio.Novedad;
import objetosNegocio.Usuario;

/**
 * Ventana para atender un reporte: muestra todo el detalle y, si el conductor avisó de un problema con un viaje, ofrece la acción
 * lógica (reasignar otro bus, cancelar el viaje con reembolso o denegar). Para el resto solo se responde.
 */
public class DialogoResponderNovedad extends JDialog {
    private static final String SOLO_RESPONDER = "Solo responder";
    private static final String REASIGNAR = "Aceptar y reasignar otro bus";
    private static final String CANCELAR = "Aceptar y cancelar el viaje (reembolsa a los pasajeros)";
    private static final String DENEGAR = "Denegar: el conductor debe realizar el viaje";

    private final Usuario actor;
    private final Novedad novedad;
    private final Runnable alTerminar;
    private final JComboBox<String> cmbAccion = new JComboBox<>();
    private final JComboBox<String> cmbEstado = new JComboBox<>(new String[]{Novedad.RESUELTA, Novedad.EN_REVISION});
    private final JTextArea txtRespuesta = new JTextArea(4, 40);
    private final JLabel lblContador = Tema.etiqueta("0 / 200");
    private final JLabel lblMensaje = Tema.mensaje();
    private final JLabel lblAyuda = Tema.etiqueta(" ");
    private final JButton btnEnviar = Tema.boton("Enviar respuesta", true);

    public DialogoResponderNovedad(Window padre, Usuario actor, Novedad novedad, String remitente, Runnable alTerminar) {
        super(padre, "Atender reporte " + novedad.getIdNovedad(), Dialog.ModalityType.APPLICATION_MODAL);
        this.actor = actor;
        this.novedad = novedad;
        this.alTerminar = alTerminar;
        setLayout(new BorderLayout());
        add(Tema.encabezado("Reporte " + novedad.getIdNovedad(), ControladorNovedades.TIPOS.getOrDefault(novedad.getTipo(), novedad.getTipo())), BorderLayout.NORTH);

        JTextArea detalle = new JTextArea(armarDetalle(remitente));
        detalle.setEditable(false);
        detalle.setLineWrap(true);
        detalle.setWrapStyleWord(true);
        detalle.setFont(Tema.fuente(Font.PLAIN, 14f));
        detalle.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane spDetalle = new JScrollPane(detalle);
        spDetalle.setBorder(BorderFactory.createTitledBorder("Lo que reportó"));
        spDetalle.setPreferredSize(new Dimension(640, 190));

        boolean resuelta = Novedad.RESUELTA.equals(novedad.getEstado());
        boolean deViaje = ControladorDespacho.esReporteDeViaje(novedad) && !resuelta;
        if (deViaje) {
            cmbAccion.addItem(REASIGNAR);
            cmbAccion.addItem(CANCELAR);
            cmbAccion.addItem(DENEGAR);
            cmbAccion.addItem(SOLO_RESPONDER);
            if (!"FALLA_MECANICA".equals(novedad.getTipo())) {
                cmbAccion.setSelectedItem(CANCELAR);
            }
        } else {
            cmbAccion.addItem(SOLO_RESPONDER);
        }
        cmbAccion.addActionListener(e -> actualizarCampos());

        txtRespuesta.setLineWrap(true);
        txtRespuesta.setWrapStyleWord(true);
        txtRespuesta.setFont(Tema.fuente(Font.PLAIN, 14f));
        txtRespuesta.setText(novedad.getRespuesta() == null ? "" : novedad.getRespuesta());
        txtRespuesta.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void c() {
                int n = txtRespuesta.getText().length();
                lblContador.setText(n + " / 200");
                lblContador.setForeground(n > 200 ? Tema.ERROR : Tema.TEXTO_SUAVE);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { c(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { c(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { c(); }
        });
        JScrollPane spRespuesta = new JScrollPane(txtRespuesta);
        spRespuesta.setBorder(BorderFactory.createLineBorder(Tema.BORDE));

        JPanel f = Tema.panelFormulario();
        int fila = 0;
        if (deViaje) {
            Tema.fila(f, fila++, "¿Qué hacer con el viaje " + novedad.getIdViaje() + "?", cmbAccion);
            Tema.fila(f, fila++, "", lblAyuda);
        }
        Tema.fila(f, fila++, "Respuesta", spRespuesta);
        Tema.fila(f, fila++, "", lblContador);
        Tema.fila(f, fila++, "Estado del reporte", cmbEstado);

        btnEnviar.addActionListener(e -> enviar());
        JButton pdf = Tema.boton("Descargar PDF", false);
        pdf.addActionListener(e -> Descargas.guardarPdf(this, "Descargar reporte", control.ComprobantesPdf.nombreSugerido("Reporte", novedad.getIdNovedad()),
                destino -> new control.ComprobantesPdf().novedad(destino, novedad)));
        JButton cerrar = Tema.boton("Cerrar", false);
        cerrar.addActionListener(e -> dispose());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(cerrar);
        botones.add(pdf);
        if (!resuelta) {
            botones.add(btnEnviar);
        }

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(BorderFactory.createEmptyBorder(10, 20, 12, 20));
        centro.add(spDetalle, BorderLayout.NORTH);
        centro.add(f, BorderLayout.CENTER);
        JPanel sur = new JPanel(new BorderLayout(0, 6));
        sur.setOpaque(false);
        sur.add(lblMensaje, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.CENTER);
        centro.add(sur, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);

        if (resuelta) {
            txtRespuesta.setEditable(false);
            cmbEstado.setEnabled(false);
            Tema.mostrarMensaje(lblMensaje, "Este reporte ya fue resuelto.", true);
        }
        actualizarCampos();
        setSize(new Dimension(720, deViaje ? 700 : 640));
        setResizable(false);
        setLocationRelativeTo(padre);
    }

    private String armarDetalle(String remitente) {
        String fecha = novedad.getFecha() == null ? "" : String.format("%02d/%02d/%04d %s", novedad.getFecha().getDia(), novedad.getFecha().getMes(), novedad.getFecha().getAnio(), novedad.getHora());
        String viaje = novedad.getIdViaje() == null || novedad.getIdViaje().isEmpty() ? "ninguno" : novedad.getIdViaje();
        return "De: " + remitente + "\nFecha: " + fecha + "\nViaje: " + viaje + "\nAsunto: " + novedad.getAsunto() + "\n\n" + novedad.getMensaje();
    }

    private void actualizarCampos() {
        String accion = (String) cmbAccion.getSelectedItem();
        boolean auto = REASIGNAR.equals(accion) || CANCELAR.equals(accion);
        txtRespuesta.setEnabled(!auto);
        cmbEstado.setEnabled(SOLO_RESPONDER.equals(accion));
        if (auto) {
            txtRespuesta.setText("");
        }
        if (REASIGNAR.equals(accion)) {
            lblAyuda.setText("<html>Busca un bus activo, libre a esa hora, de la misma empresa y con los mismos asientos. Si el reporte es una falla mecánica, el bus dañado queda INACTIVO. El conductor recibe un aviso.</html>");
            btnEnviar.setText("Reasignar bus");
        } else if (CANCELAR.equals(accion)) {
            lblAyuda.setText("<html>El viaje se cancela, se devuelve el dinero a las billeteras de los pasajeros (con nota de crédito) y sale de los pagos al conductor.</html>");
            btnEnviar.setText("Cancelar viaje");
        } else if (DENEGAR.equals(accion)) {
            lblAyuda.setText("<html>Escribe el motivo: el conductor lo recibe como aviso y debe realizar el viaje.</html>");
            btnEnviar.setText("Denegar");
        } else {
            lblAyuda.setText(" ");
            btnEnviar.setText("Enviar respuesta");
        }
    }

    private void enviar() {
        String accion = (String) cmbAccion.getSelectedItem();
        ResultadoOperacion r;
        ControladorDespacho despacho = new ControladorDespacho();
        if (REASIGNAR.equals(accion)) {
            r = despacho.reasignarBus(actor, novedad.getIdNovedad());
            if (!r.isExito()) {
                int cancelar = JOptionPane.showConfirmDialog(this, r.getMensaje() + "\n\n¿Cancelar el viaje y reembolsar a los pasajeros?", "No se pudo reasignar",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (cancelar != JOptionPane.YES_OPTION) {
                    return;
                }
                r = despacho.aprobarCancelacion(actor, novedad.getIdNovedad());
            }
        } else if (CANCELAR.equals(accion)) {
            if (JOptionPane.showConfirmDialog(this, "Se cancelará el viaje " + novedad.getIdViaje() + " y se reembolsará a los pasajeros. ¿Continuar?",
                    "Cancelar viaje", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
            r = despacho.aprobarCancelacion(actor, novedad.getIdNovedad());
        } else if (DENEGAR.equals(accion)) {
            r = despacho.denegarCancelacion(actor, novedad.getIdNovedad(), txtRespuesta.getText());
        } else {
            r = new ControladorNovedades().responder(actor, novedad.getIdNovedad(), txtRespuesta.getText(), (String) cmbEstado.getSelectedItem());
        }
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        Tema.info(this, r.getMensaje());
        alTerminar.run();
        dispose();
    }
}
