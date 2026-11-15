package InterfacesUsuario;

import control.ControladorUsuarios;
import control.ResultadoOperacion;
import excepciones.Validador;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import objetosNegocio.Rol;
import objetosNegocio.Usuario;
import objetosServicio.ConfiguracionAdmin;

/**
 * Primer ingreso obligatorio de una cuenta con contraseña temporal (administrador principal y colaboradores nuevos).
 * Pide la contraseña nueva y, si la cuenta aún no la tiene, una pregunta de seguridad personal para recuperar la clave
 * (y la cédula real en la cuenta del administrador principal, que nace con una cédula reservada). Si se cancela, no se entra.
 */
public class DialogoCambioClave extends JDialog {
    private final ControladorUsuarios controlador = new ControladorUsuarios();
    private final Usuario usuario;
    private final boolean pedirCedula;
    private final boolean pedirPregunta;
    private final JPasswordField txtActual = Tema.clave(24);
    private final JPasswordField txtNueva = Tema.clave(24);
    private final JPasswordField txtConfirmar = Tema.clave(24);
    private final JLabel lblFortaleza = Tema.mensaje();
    private final JTextField txtCedula = Tema.campo(24);
    private final JLabel lblProvincia = Tema.etiqueta(" ");
    private final JComboBox<String> cmbPregunta;
    private final JTextField txtRespuesta = Tema.campo(24);
    private final JLabel lblMensaje = Tema.mensaje();
    private final ValidacionUI.Reglas reglas = new ValidacionUI.Reglas();
    private boolean cambiada;

    public DialogoCambioClave(Window padre, Usuario usuario) {
        super(padre, "Primer ingreso: configura tu cuenta", Dialog.ModalityType.APPLICATION_MODAL);
        this.usuario = usuario;
        this.pedirCedula = ConfiguracionAdmin.CEDULA_RESERVADA.equals(usuario.getCedula());
        this.pedirPregunta = !usuario.tienePreguntaSeguridad();
        String[] preguntas = usuario.getRol() == Rol.ADMINISTRADOR ? Tema.PREGUNTAS_ADMIN : Tema.PREGUNTAS;
        cmbPregunta = new JComboBox<>(preguntas);
        cmbPregunta.setEditable(true);
        cmbPregunta.setSelectedItem(null);
        cmbPregunta.setFont(Tema.fuente(Font.PLAIN, 13f));
        setIconImage(IconoApp.crear(64));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(Tema.encabezado("Configura tu cuenta", "Hola " + usuario.getNombre() + ": tu contraseña actual es temporal"), BorderLayout.NORTH);

        JLabel errActual = ValidacionUI.etiquetaError();
        JLabel errNueva = ValidacionUI.etiquetaError();
        JLabel errConfirmar = ValidacionUI.etiquetaError();
        JLabel errCedula = ValidacionUI.etiquetaError();
        JLabel errPregunta = ValidacionUI.etiquetaError();
        JLabel errRespuesta = ValidacionUI.etiquetaError();
        JPanel f = Tema.panelFormulario();
        int fila = 0;
        fila = ValidacionUI.fila(f, fila, "Contraseña temporal *", txtActual, errActual);
        fila = ValidacionUI.fila(f, fila, "Nueva contraseña *", txtNueva, errNueva);
        Tema.fila(f, fila++, "", lblFortaleza);
        fila = ValidacionUI.fila(f, fila, "Confirmar nueva *", txtConfirmar, errConfirmar);
        JCheckBox chkMostrar = new JCheckBox("Mostrar contraseñas");
        chkMostrar.setOpaque(false);
        chkMostrar.setFont(Tema.fuente(Font.PLAIN, 12f));
        char eco = txtActual.getEchoChar();
        chkMostrar.addActionListener(e -> {
            char c = chkMostrar.isSelected() ? (char) 0 : eco;
            txtActual.setEchoChar(c);
            txtNueva.setEchoChar(c);
            txtConfirmar.setEchoChar(c);
        });
        Tema.fila(f, fila++, "", chkMostrar);
        Tema.vincularFortaleza(txtNueva, lblFortaleza);

        reglas.agregar(txtActual, errActual, () -> new String(txtActual.getPassword()).isEmpty() ? "Escribe tu contraseña temporal" : null);
        reglas.agregar(txtNueva, errNueva, () -> {
            String nueva = new String(txtNueva.getPassword());
            String problema = Validador.errorContrasena(nueva);
            if (problema == null && nueva.equals(new String(txtActual.getPassword()))) {
                problema = "La nueva contraseña debe ser distinta de la temporal";
            }
            return problema;
        });
        reglas.agregar(txtConfirmar, errConfirmar, () -> new String(txtNueva.getPassword()).equals(new String(txtConfirmar.getPassword()))
                ? null : "Las contraseñas no coinciden");

        if (pedirCedula) {
            ValidacionUI.soloDigitos(txtCedula, 10);
            ValidacionUI.mostrarProvincia(txtCedula, lblProvincia);
            lblProvincia.setForeground(Tema.TEXTO_SUAVE);
            fila = ValidacionUI.fila(f, fila, "Tu cédula real *", txtCedula, errCedula);
            Tema.fila(f, fila++, "", lblProvincia);
            reglas.agregar(txtCedula, errCedula, () -> Validador.errorCedula(txtCedula.getText()));
        }
        if (pedirPregunta) {
            ValidacionUI.limite(txtRespuesta, 40);
            fila = ValidacionUI.fila(f, fila, "Pregunta personal *", cmbPregunta, errPregunta);
            fila = ValidacionUI.fila(f, fila, "Respuesta *", txtRespuesta, errRespuesta);
            reglas.agregar(cmbPregunta, errPregunta, () -> {
                String p = textoPregunta();
                String problema = Validador.errorPreguntaSeguridad(p);
                return problema == null && !p.trim().endsWith("?") ? "Termina la pregunta con el signo ?" : problema;
            });
            reglas.agregar(txtRespuesta, errRespuesta, () -> {
                String problema = Validador.errorRespuestaSeguridad(txtRespuesta.getText());
                return problema == null && txtRespuesta.getText().trim().equalsIgnoreCase(new String(txtNueva.getPassword()))
                        ? "La respuesta no puede ser igual a tu contraseña" : problema;
            });
        }

        JLabel nota = Tema.nota(560, "<html>Por seguridad debes reemplazar la contraseña temporal. Usa mayúscula, minúscula y número (8 a 40 caracteres)."
                + (pedirPregunta ? "<br><br><b>Pregunta de seguridad:</b> elige una de la lista o escribe la tuya. Será lo único que te permita recuperar "
                + "tu contraseña si la olvidas, así que usa una respuesta que solo tú sepas." : "") + "</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);

        JButton guardar = Tema.boton("Guardar y entrar", true);
        guardar.addActionListener(e -> guardar());
        JButton cancelar = Tema.boton("Cancelar", false);
        cancelar.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(guardar);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(cancelar);
        botones.add(guardar);

        JPanel centro = new ValidacionUI.PanelDesplazable(new BorderLayout(0, 8));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(BorderFactory.createEmptyBorder(14, 34, 14, 34));
        centro.add(nota, BorderLayout.NORTH);
        centro.add(f, BorderLayout.CENTER);
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
        Dimension pantalla = Toolkit.getDefaultToolkit().getScreenSize();
        setPreferredSize(new Dimension(680, Math.min(pantalla.height - 80, 700)));
        pack();
        setLocationRelativeTo(padre);
    }

    private String textoPregunta() {
        Object o = cmbPregunta.getEditor().getItem();
        return o == null ? "" : o.toString();
    }

    public boolean fueCambiada() {
        return cambiada;
    }

    private void guardar() {
        if (!reglas.validar()) {
            Tema.mostrarMensaje(lblMensaje, "Revisa los campos marcados en rojo.", false);
            return;
        }
        if (pedirCedula || pedirPregunta) {
            ResultadoOperacion datos = controlador.completarCuentaInicial(usuario.getIdUsuario(), txtCedula.getText(), textoPregunta(), txtRespuesta.getText());
            if (!datos.isExito()) {
                Tema.mostrarMensaje(lblMensaje, datos.getMensaje(), false);
                return;
            }
        }
        ResultadoOperacion r = controlador.cambiarContrasena(usuario.getIdUsuario(), new String(txtActual.getPassword()),
                new String(txtNueva.getPassword()), new String(txtConfirmar.getPassword()));
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        cambiada = true;
        Tema.info(this, "Listo: tu contraseña y tus datos de seguridad quedaron guardados.");
        dispose();
    }
}
