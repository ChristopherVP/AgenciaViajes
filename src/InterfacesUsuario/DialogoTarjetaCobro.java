package InterfacesUsuario;

import control.ControladorTarjetas;
import control.ResultadoOperacion;
import excepciones.Validador;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import objetosNegocio.Usuario;
import objetosServicio.Bancos;
import objetosServicio.Fecha;

/** Registro o cambio de la tarjeta de DÉBITO del conductor, donde se le paga el 80 % de cada viaje realizado. */
public class DialogoTarjetaCobro extends JDialog {
    private final ControladorTarjetas controlador = new ControladorTarjetas();
    private final Usuario conductor;
    private final Runnable alGuardar;
    private final JComboBox<String> cmbBanco = new JComboBox<>();
    private final JTextField txtNumero = Tema.campo(22);
    private final SelectorFecha selVence = new SelectorFecha(LocalDate.now().getYear(), LocalDate.now().getYear() + 12, true);
    private final JLabel lblMarca = Tema.etiqueta(" ");
    private final JLabel lblMensaje = Tema.mensaje();
    private final ValidacionUI.Reglas reglas = new ValidacionUI.Reglas();

    public DialogoTarjetaCobro(Window padre, Usuario conductor, Runnable alGuardar) {
        super(padre, "Tarjeta de cobro", Dialog.ModalityType.APPLICATION_MODAL);
        this.conductor = conductor;
        this.alGuardar = alGuardar;
        setIconImage(IconoApp.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado("Tarjeta de débito para tus pagos", conductor.getNombre()), BorderLayout.NORTH);
        cmbBanco.addItem("Selecciona tu banco...");
        Bancos.LISTA.forEach(cmbBanco::addItem);
        cmbBanco.setFont(Tema.fuente(Font.PLAIN, 13f));
        ValidacionUI.tarjeta(txtNumero);
        lblMarca.setForeground(Tema.TEXTO_SUAVE);
        txtNumero.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void act() {
                String m = Bancos.marca(txtNumero.getText().replace(" ", ""));
                lblMarca.setText(txtNumero.getText().length() < 2 ? " " : "Tarjeta " + m);
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) { act(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { act(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { act(); }
        });
        JLabel errBanco = ValidacionUI.etiquetaError();
        JLabel errNumero = ValidacionUI.etiquetaError();
        JLabel errVence = ValidacionUI.etiquetaError();
        JPanel f = Tema.panelFormulario();
        int i = 0;
        i = ValidacionUI.fila(f, i, "Titular", titularFijo(), ValidacionUI.etiquetaError());
        i = ValidacionUI.fila(f, i, "Banco *", cmbBanco, errBanco);
        i = ValidacionUI.fila(f, i, "N.º de tarjeta *", txtNumero, errNumero);
        Tema.fila(f, i++, "", lblMarca);
        ValidacionUI.fila(f, i, "Vence *", selVence, errVence);
        reglas.agregar(cmbBanco, errBanco, () -> cmbBanco.getSelectedIndex() <= 0 ? "Elige tu banco o cooperativa" : null);
        reglas.agregar(txtNumero, errNumero, () -> ControladorTarjetas.errorNumero(txtNumero.getText()));
        reglas.agregar(selVence, errVence, () -> {
            Fecha v = selVence.getFecha();
            if (v == null) return "Elige el mes y el año de vencimiento";
            return java.time.YearMonth.of(v.getAnio(), v.getMes()).isBefore(java.time.YearMonth.now()) ? "La tarjeta está vencida" : null;
        });
        JLabel nota = Tema.nota(480, "<html>Solo tarjetas de <b>débito</b> Visa o Mastercard <b>a tu nombre</b>. Recibirás el <b>80 %</b> de lo que paguen los pasajeros en cada viaje "
                + "que realices. Guardamos solo el banco, la marca y los últimos 4 dígitos; el número completo no se almacena.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);
        JButton guardar = Tema.boton("Guardar tarjeta", true);
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
        setPreferredSize(new Dimension(640, 560));
        pack();
        setLocationRelativeTo(padre);
    }

    private JTextField titularFijo() {
        JTextField t = Tema.campo(22);
        t.setText(conductor.getNombre().toUpperCase());
        t.setEnabled(false);
        return t;
    }

    private String fechaTexto(boolean valido) {
        Fecha v = selVence.getFecha();
        return v == null ? "" : String.format("%02d/%02d", v.getMes(), v.getAnio() % 100);
    }

    private void guardar() {
        if (!reglas.validar()) {
            Tema.mostrarMensaje(lblMensaje, "Revisa los campos marcados en rojo.", false);
            return;
        }
        ResultadoOperacion r = controlador.cambiarTarjetaCobro(conductor, String.valueOf(cmbBanco.getSelectedItem()), txtNumero.getText(), fechaTexto(true));
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        Tema.info(this, r.getMensaje());
        alGuardar.run();
        dispose();
    }
}
