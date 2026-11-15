package InterfacesUsuario;

import control.ControladorBilletera;
import control.ResultadoOperacion;
import excepciones.Validador;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import objetosNegocio.Usuario;
import objetosServicio.Fecha;

/**
 * Solicitud de recarga de la billetera por tarjeta o efectivo. Queda pendiente hasta que el administrador la apruebe.
 * Valida como un pago real: monto con 2 decimales, titular en mayúsculas, número con algoritmo de Luhn y marca detectada,
 * vencimiento elegido en listas (no puede estar vencida) y CVV de 3 o 4 dígitos según la marca.
 */
public class DialogoRecarga extends JDialog {
    private final ControladorBilletera billetera = new ControladorBilletera();
    private final Usuario usuario;
    private final Runnable alEnviar;
    private final JRadioButton rbTarjeta = new JRadioButton("Tarjeta", true);
    private final JRadioButton rbEfectivo = new JRadioButton("Efectivo");
    private final JTextField txtMonto = Tema.campo(14);
    private final JTextField txtTitular = Tema.campo(20);
    private final JTextField txtNumero = Tema.campo(20);
    private final JLabel lblMarca = Tema.etiqueta(" ");
    private final SelectorFecha selVence = new SelectorFecha(LocalDate.now().getYear(), LocalDate.now().getYear() + 12, true);
    private final JPasswordField txtCvv = Tema.clave(6);
    private final JTextField txtComprobante = Tema.campo(20);
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel panelMetodo = new JPanel(tarjetas);
    private final JLabel lblMensaje = Tema.mensaje();
    private final ValidacionUI.Reglas reglas = new ValidacionUI.Reglas();

    public DialogoRecarga(Window padre, Usuario usuario, Runnable alEnviar) {
        super(padre, "Solicitar recarga", Dialog.ModalityType.APPLICATION_MODAL);
        this.usuario = usuario;
        this.alEnviar = alEnviar;
        setIconImage(IconoApp.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado("Recargar billetera", "Tu recarga será revisada por el administrador"), BorderLayout.NORTH);

        ButtonGroup grupo = new ButtonGroup();
        JPanel metodos = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        metodos.setOpaque(false);
        for (JRadioButton rb : new JRadioButton[]{rbTarjeta, rbEfectivo}) {
            rb.setOpaque(false);
            rb.setFont(Tema.fuente(Font.BOLD, 14f));
            grupo.add(rb);
            metodos.add(rb);
            rb.addActionListener(e -> {
                tarjetas.show(panelMetodo, rbTarjeta.isSelected() ? "tarjeta" : "efectivo");
                reglas.limpiar();
                Tema.mostrarMensaje(lblMensaje, " ", true);
            });
        }

        ValidacionUI.monto(txtMonto, 6);
        ValidacionUI.titular(txtTitular, 26);
        ValidacionUI.tarjeta(txtNumero);
        ValidacionUI.soloDigitos(txtCvv, 4);
        ValidacionUI.soloDigitos(txtComprobante, 12);
        lblMarca.setForeground(Tema.TEXTO_SUAVE);
        txtNumero.getDocument().addDocumentListener(new DocumentListener() {
            private void actualizar() {
                String marca = marca(digitos());
                lblMarca.setText(marca.isEmpty() ? " " : "Tarjeta " + marca);
            }

            public void insertUpdate(DocumentEvent e) { actualizar(); }
            public void removeUpdate(DocumentEvent e) { actualizar(); }
            public void changedUpdate(DocumentEvent e) { actualizar(); }
        });

        JLabel errMonto = ValidacionUI.etiquetaError();
        JLabel errTitular = ValidacionUI.etiquetaError();
        JLabel errNumero = ValidacionUI.etiquetaError();
        JLabel errVence = ValidacionUI.etiquetaError();
        JLabel errCvv = ValidacionUI.etiquetaError();
        JLabel errComprobante = ValidacionUI.etiquetaError();

        JPanel montoPanel = Tema.panelFormulario();
        ValidacionUI.fila(montoPanel, 0, String.format("Monto (%s a %s)", Tema.dinero(ControladorBilletera.MONTO_MINIMO), Tema.dinero(ControladorBilletera.MONTO_MAXIMO)), txtMonto, errMonto);

        JPanel t = Tema.panelFormulario();
        int f = 0;
        f = ValidacionUI.fila(t, f, "Titular", txtTitular, errTitular);
        f = ValidacionUI.fila(t, f, "Número de tarjeta", txtNumero, errNumero);
        Tema.fila(t, f++, "", lblMarca);
        f = ValidacionUI.fila(t, f, "Vence", selVence, errVence);
        ValidacionUI.fila(t, f, "CVV", txtCvv, errCvv);
        JLabel seguridad = Tema.nota(500, "<html>Por seguridad solo guardamos la marca y los últimos 4 dígitos. El número completo y el CVV <b>no</b> se almacenan.</html>");
        seguridad.setForeground(Tema.TEXTO_SUAVE);
        JPanel pt = new JPanel(new BorderLayout(0, 6));
        pt.setOpaque(false);
        pt.add(t, BorderLayout.NORTH);
        pt.add(seguridad, BorderLayout.CENTER);

        JPanel e = Tema.panelFormulario();
        ValidacionUI.fila(e, 0, "N.º de comprobante", txtComprobante, errComprobante);
        JLabel ayuda = Tema.nota(500, "<html>Paga en ventanilla o con un agente autorizado y escribe el número del comprobante (6 a 12 dígitos). "
                + "El administrador verificará el pago antes de acreditar el saldo.</html>");
        ayuda.setForeground(Tema.TEXTO_SUAVE);
        JPanel pe = new JPanel(new BorderLayout(0, 6));
        pe.setOpaque(false);
        pe.add(e, BorderLayout.NORTH);
        pe.add(ayuda, BorderLayout.CENTER);

        panelMetodo.setOpaque(false);
        panelMetodo.add(pt, "tarjeta");
        panelMetodo.add(pe, "efectivo");

        reglas.agregar(txtMonto, errMonto, this::errorMonto);
        reglas.agregar(txtTitular, errTitular, () -> Validador.errorNombre(txtTitular.getText()) == null ? null
                : "Escribe el nombre y apellido del titular tal como aparece en la tarjeta");
        reglas.agregar(txtNumero, errNumero, () -> {
            String d = digitos();
            if (d.length() < 13) {
                return "El número de tarjeta debe tener entre 13 y 19 dígitos";
            }
            return Validador.esTarjetaValida(d) ? null : "El número de tarjeta no es válido, revísalo";
        });
        reglas.agregar(selVence, errVence, this::errorVencimiento);
        reglas.agregar(txtCvv, errCvv, () -> {
            boolean amex = marca(digitos()).equals("American Express");
            return new String(txtCvv.getPassword()).matches(amex ? "\\d{4}" : "\\d{3}") ? null
                    : (amex ? "El CVV de American Express tiene 4 dígitos" : "El CVV tiene 3 dígitos");
        });
        reglas.agregar(txtComprobante, errComprobante, () -> txtComprobante.getText().matches("\\d{6,12}") ? null
                : "El comprobante debe tener entre 6 y 12 dígitos");

        JButton enviar = Tema.boton("Enviar solicitud", true);
        enviar.addActionListener(ev -> enviar());
        JButton cancelar = Tema.boton("Cancelar", false);
        cancelar.addActionListener(ev -> dispose());
        getRootPane().setDefaultButton(enviar);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(cancelar);
        botones.add(enviar);

        JPanel centro = new ValidacionUI.PanelDesplazable(new BorderLayout(0, 10));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(BorderFactory.createEmptyBorder(16, 28, 16, 28));
        JPanel arriba = new JPanel(new BorderLayout(0, 6));
        arriba.setOpaque(false);
        arriba.add(metodos, BorderLayout.NORTH);
        arriba.add(montoPanel, BorderLayout.CENTER);
        centro.add(arriba, BorderLayout.NORTH);
        centro.add(panelMetodo, BorderLayout.CENTER);
        JPanel sur = new JPanel(new BorderLayout(0, 6));
        sur.setOpaque(false);
        sur.add(lblMensaje, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.CENTER);
        centro.add(sur, BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(centro);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(640, 700));
        pack();
        setLocationRelativeTo(padre);
    }

    private String digitos() {
        return txtNumero.getText().replace(" ", "");
    }

    private static String marca(String d) {
        if (d.startsWith("4")) return "Visa";
        if (d.matches("^(5[1-5].*|2(2[2-9]|[3-6]\\d|7[01]).*|2720.*)")) return "Mastercard";
        if (d.startsWith("34") || d.startsWith("37")) return "American Express";
        if (d.startsWith("36") || d.startsWith("30") || d.startsWith("38")) return "Diners Club";
        return d.length() >= 2 ? "(marca no reconocida)" : "";
    }

    private String errorMonto() {
        String texto = txtMonto.getText().trim();
        if (texto.isEmpty() || texto.equals(".")) {
            return "Escribe el monto que quieres recargar";
        }
        double monto;
        try {
            monto = Double.parseDouble(texto);
        } catch (NumberFormatException ex) {
            return "Escribe un monto válido, por ejemplo 25.50";
        }
        if (monto < ControladorBilletera.MONTO_MINIMO || monto > ControladorBilletera.MONTO_MAXIMO) {
            return String.format("El monto debe estar entre %s y %s", Tema.dinero(ControladorBilletera.MONTO_MINIMO), Tema.dinero(ControladorBilletera.MONTO_MAXIMO));
        }
        return null;
    }

    private String errorVencimiento() {
        Fecha v = selVence.getFecha();
        if (v == null) {
            return "Elige el mes y el año de vencimiento";
        }
        LocalDate hoy = LocalDate.now();
        boolean vencida = v.getAnio() < hoy.getYear() || (v.getAnio() == hoy.getYear() && v.getMes() < hoy.getMonthValue());
        return vencida ? "La tarjeta está vencida" : null;
    }

    private void enviar() {
        if (!reglas.validar()) {
            Tema.mostrarMensaje(lblMensaje, "Revisa los campos marcados en rojo.", false);
            return;
        }
        double monto = Double.parseDouble(txtMonto.getText().trim());
        ResultadoOperacion r;
        if (rbTarjeta.isSelected()) {
            Fecha v = selVence.getFecha();
            String vence = String.format("%02d/%02d", v.getMes(), v.getAnio() % 100);
            r = billetera.solicitarRecargaTarjeta(usuario.getIdPerfil(), monto, txtTitular.getText(), txtNumero.getText(), vence, new String(txtCvv.getPassword()));
        } else {
            r = billetera.solicitarRecargaEfectivo(usuario.getIdPerfil(), monto, txtComprobante.getText());
        }
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        txtNumero.setText("");
        txtCvv.setText("");
        alEnviar.run();
        Tema.info(this, r.getMensaje());
        if (r.getDatos() instanceof objetosNegocio.Movimiento movimiento && movimiento.getEstado().equalsIgnoreCase(objetosNegocio.Movimiento.APROBADA)) {
            objetosNegocio.Comprobante factura = new control.ControladorFacturacion().buscarPorReferencia(objetosNegocio.Comprobante.RECARGA, movimiento.getIdMovimiento());
            if (factura != null) {
                Window dueno = getOwner();
                dispose();
                new DialogoFactura(dueno, factura).setVisible(true);
                return;
            }
        }
        dispose();
    }
}
