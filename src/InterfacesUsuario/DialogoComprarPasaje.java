package InterfacesUsuario;

import control.ControladorBilletera;
import control.ControladorCompras;
import control.ControladorCompras.Oferta;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import objetosNegocio.Usuario;

/** Compra de un pasaje: elegir asientos, ver el total y pagar con el saldo de la billetera. */
public class DialogoComprarPasaje extends JDialog {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ControladorCompras compras = new ControladorCompras();
    private final ControladorBilletera billetera = new ControladorBilletera();
    private final Usuario usuario;
    private final Oferta oferta;
    private final Runnable alCambiar;
    private List<Integer> asientos = new ArrayList<>();
    private javax.swing.Timer vigilante;
    private JButton btnElegirRef;

    private final JLabel lblAsientos = Tema.titulo("Ninguno", 15f);
    private final JLabel lblTotal = Tema.titulo("—", 22f);
    private final JLabel lblSaldo = Tema.etiqueta(" ");
    private final JLabel lblAviso = Tema.mensaje();
    private final JButton btnPagar = Tema.boton("Pagar con mi billetera", true);
    private final JButton btnRecargar = Tema.boton("Recargar billetera", false);
    private final JCheckBox chkSinDevolucion = new JCheckBox("Entiendo que mi compra es definitiva: no hay cancelaciones ni devoluciones");

    public DialogoComprarPasaje(Window padre, Usuario usuario, Oferta oferta, Runnable alCambiar) {
        super(padre, "Comprar pasaje", Dialog.ModalityType.APPLICATION_MODAL);
        this.usuario = usuario;
        this.oferta = oferta;
        this.alCambiar = alCambiar;
        setIconImage(IconoApp.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado(oferta.getOrigen() + " → " + oferta.getDestino(), oferta.getEmpresa()), BorderLayout.NORTH);

        JPanel info = Tema.panelFormulario();
        String llegada = oferta.getViaje().getFechaLlegada() == null ? "—"
                : String.format("%02d/%02d/%04d %s", oferta.getViaje().getFechaLlegada().getDia(), oferta.getViaje().getFechaLlegada().getMes(),
                oferta.getViaje().getFechaLlegada().getAnio(), oferta.getViaje().getHoraLlegada() == null ? "" : oferta.getViaje().getHoraLlegada());
        Tema.fila(info, 0, "Salida", Tema.titulo(oferta.getSalida().format(FORMATO), 14f));
        Tema.fila(info, 1, "Llegada estimada", Tema.etiqueta(llegada));
        Tema.fila(info, 2, "Precio base por asiento", Tema.etiqueta(Tema.dinero(oferta.getPrecioUnitario())));
        Tema.fila(info, 3, "Asientos libres", Tema.etiqueta(String.valueOf(oferta.getViaje().getAsientosDisponibles())));
        JLabel nota = Tema.nota(600, "<html>Las tarifas preferenciales (niños, adolescentes, tercera edad y discapacidad) se aplican automáticamente en el total.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);

        JButton btnElegir = Tema.boton("Elegir asientos...", false);
        btnElegirRef = btnElegir;
        btnElegir.addActionListener(e -> elegirAsientos());
        JPanel seleccion = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        seleccion.setOpaque(false);
        JButton btnMapa = Tema.boton("Ver ruta en el mapa", false);
        btnMapa.addActionListener(e -> verMapa());
        seleccion.add(btnElegir);
        seleccion.add(lblAsientos);
        seleccion.add(btnMapa);

        JPanel totales = Tema.panelFormulario();
        Tema.fila(totales, 0, "Total a pagar", lblTotal);
        Tema.fila(totales, 1, "Saldo de tu billetera", lblSaldo);

        btnPagar.addActionListener(e -> pagar());
        btnRecargar.addActionListener(e -> new DialogoRecarga(this, usuario, this::actualizar).setVisible(true));
        JButton cancelar = Tema.boton("Cancelar", false);
        cancelar.addActionListener(e -> dispose());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(cancelar);
        botones.add(btnRecargar);
        botones.add(btnPagar);

        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
        JPanel medio = new JPanel(new BorderLayout(0, 8));
        medio.setOpaque(false);
        JPanel superior = new JPanel(new BorderLayout(0, 4));
        superior.setOpaque(false);
        superior.add(info, BorderLayout.NORTH);
        superior.add(nota, BorderLayout.CENTER);
        medio.add(superior, BorderLayout.NORTH);
        medio.add(seleccion, BorderLayout.CENTER);
        medio.add(totales, BorderLayout.SOUTH);
        centro.add(medio, BorderLayout.CENTER);
        JPanel sur = new JPanel(new BorderLayout(0, 6));
        sur.setOpaque(false);
        JLabel advertencia = Tema.nota(600, "<html><b>Importante:</b> los pasajes comprados <b>no se pueden cancelar ni se reembolsan</b>. "
                + "Revisa bien la ruta, el horario y tus asientos antes de pagar.</html>");
        advertencia.setForeground(new java.awt.Color(0x9A1C1C));
        chkSinDevolucion.setOpaque(false);
        chkSinDevolucion.setFont(Tema.fuente(Font.BOLD, 12f));
        chkSinDevolucion.addActionListener(e -> actualizar());
        JPanel aviso = new JPanel(new BorderLayout(0, 4));
        aviso.setOpaque(false);
        aviso.add(advertencia, BorderLayout.NORTH);
        aviso.add(chkSinDevolucion, BorderLayout.CENTER);
        aviso.add(lblAviso, BorderLayout.SOUTH);
        sur.add(aviso, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.CENTER);
        centro.add(sur, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);
        actualizar();
        // Tamaño fijo que cabe en cualquier pantalla: nada queda cortado y el contenido no se estira.
        Dimension pantalla = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(new Dimension(Math.min(700, pantalla.width - 40), Math.min(640, pantalla.height - 80)));
        setMinimumSize(getSize());
        setResizable(false);
        setLocationRelativeTo(padre);
        // La venta se cierra sola a la hora de salida aunque el diálogo siga abierto.
        vigilante = new javax.swing.Timer(1000, e -> actualizar());
        vigilante.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                vigilante.stop();
            }
        });
    }

    private void elegirAsientos() {
        DialogoMapaAsientos mapa = new DialogoMapaAsientos(this, oferta.getViaje().getIdViaje(), asientos);
        mapa.setVisible(true);
        if (!mapa.isConfirmado()) {
            return;
        }
        List<Integer> elegidos = mapa.getAsientosSeleccionados();
        if (elegidos.size() > ControladorCompras.MAX_ASIENTOS) {
            Tema.error(this, "Puedes comprar hasta " + ControladorCompras.MAX_ASIENTOS + " asientos por compra.");
            return;
        }
        asientos = new ArrayList<>(elegidos);
        actualizar();
    }

    private void actualizar() {
        ResultadoOperacion saldoR = billetera.consultarSaldo(usuario.getIdPerfil());
        double saldo = saldoR.isExito() ? (Double) saldoR.getDatos() : 0;
        lblSaldo.setText(Tema.dinero(saldo));
        lblAsientos.setText(asientos.isEmpty() ? "Ninguno" : asientos.toString().replace("[", "").replace("]", ""));
        btnPagar.setEnabled(false);
        btnRecargar.setVisible(false);
        String cerrada = compras.motivoNoComprable(oferta.getViaje());
        if (cerrada != null) {
            lblTotal.setText("—");
            btnElegirRef.setEnabled(false);
            Tema.mostrarMensaje(lblAviso, "Venta cerrada: " + cerrada, false);
            return;
        }
        if (asientos.isEmpty()) {
            lblTotal.setText("—");
            Tema.mostrarMensaje(lblAviso, "Elige al menos un asiento para continuar.", false);
            return;
        }
        ResultadoOperacion cot = compras.cotizar(usuario.getIdPerfil(), oferta.getViaje().getIdViaje(), asientos.size());
        if (!cot.isExito()) {
            lblTotal.setText("—");
            Tema.mostrarMensaje(lblAviso, cot.getMensaje(), false);
            return;
        }
        double total = (Double) cot.getDatos();
        lblTotal.setText(Tema.dinero(total));
        if (saldo + 1e-9 >= total) {
            btnPagar.setEnabled(chkSinDevolucion.isSelected());
            Tema.mostrarMensaje(lblAviso, chkSinDevolucion.isSelected() ? "Listo para pagar." : "Marca la casilla para confirmar que entiendes que no hay devoluciones.", chkSinDevolucion.isSelected());
        } else {
            btnRecargar.setVisible(true);
            Tema.mostrarMensaje(lblAviso, "Saldo insuficiente: te faltan " + Tema.dinero(total - saldo) + ". Recarga tu billetera (con tarjeta se acredita al instante).", false);
        }
    }

    private void pagar() {
        Object[] opciones = {"Sí, comprar", "Cancelar"};
        int opcion = JOptionPane.showOptionDialog(this, "Se descontarán " + lblTotal.getText() + " de tu billetera.\n\nRecuerda: una vez comprado el pasaje NO se puede cancelar\nni se devuelve el dinero.\n\n¿Confirmar la compra?",
                "Confirmar compra", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opciones, opciones[1]);
        if (opcion != 0) {
            return;
        }
        ResultadoOperacion r = compras.comprar(usuario.getIdPerfil(), oferta.getViaje().getIdViaje(), asientos);
        if (!r.isExito()) {
            Tema.error(this, r.getMensaje());
            asientos = new ArrayList<>();
            actualizar();
            return;
        }
        alCambiar.run();
        Tema.info(this, r.getMensaje());
        if (r.getDatos() instanceof objetosNegocio.Reserva reserva) {
            objetosNegocio.Comprobante factura = new control.ControladorFacturacion().buscarPorReferencia(objetosNegocio.Comprobante.PASAJE, reserva.getIdReserva());
            if (factura != null) {
                Window dueno = getOwner();
                dispose();
                new DialogoFactura(dueno, factura).setVisible(true);
                return;
            }
        }
        dispose();
    }

    private void verMapa() {
        try {
            objetosNegocio.Ruta ruta = fachadas.FachadaArchivos.getInstance().obten(oferta.getViaje().getRuta());
            if (ruta != null) {
                String detalle = "<b>Salida:</b> " + oferta.getSalida().format(FORMATO);
                new DialogoMapaRuta(this, ruta, detalle).setVisible(true);
            }
        } catch (Exception e) {
            Tema.error(this, "No se pudo cargar la ruta: " + e.getMessage());
        }
    }
}
