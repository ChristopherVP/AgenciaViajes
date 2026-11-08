package InterfacesUsuario;

import control.ControladorUsuarios;
import control.ResultadoOperacion;
import excepciones.Validador;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import objetosNegocio.Cargo;
import objetosNegocio.Empresa;
import objetosNegocio.Rol;
import objetosNegocio.Usuario;

/**
 * Alta y edición de administradores colaboradores con su cargo. Los pasajeros y conductores NO se crean aquí:
 * se registran solos desde "Crear cuenta". Solo el administrador principal crea administradores. La contraseña
 * que se asigna es temporal y la persona debe cambiarla (y elegir su pregunta personal) al ingresar por primera vez.
 */
public class DialogoFormularioUsuario extends JDialog {
    private final ControladorUsuarios controlador = new ControladorUsuarios();
    private final Usuario actor;
    private final Usuario editando;
    private final Runnable alGuardar;

    private final JTextField txtLogin = Tema.campo(22);
    private final JTextField txtNombre = Tema.campo(22);
    private final JTextField txtCedula = Tema.campo(22);
    private final JLabel lblProvincia = Tema.etiqueta(" ");
    private final JPasswordField txtClave = Tema.clave(22);
    private final JPasswordField txtConfirmar = Tema.clave(22);
    private final JLabel lblFortaleza = Tema.mensaje();
    private final JTextField txtEmail = Tema.campo(22);
    private final JTextField txtTelefono = Tema.campo(22);
    private final JComboBox<Cargo> cmbCargo = new JComboBox<>();
    private final JComboBox<String> cmbEstado = new JComboBox<>(new String[]{"ACTIVO", "INACTIVO", "PENDIENTE"});
    private final JLabel lblMensaje = Tema.mensaje();
    private final ValidacionUI.Reglas reglas = new ValidacionUI.Reglas();

    public DialogoFormularioUsuario(Window padre, Usuario actor, Usuario editando, List<Empresa> empresas, Runnable alGuardar) {
        super(padre, editando == null ? "Nuevo administrador" : "Editar usuario", Dialog.ModalityType.APPLICATION_MODAL);
        this.actor = actor;
        this.editando = editando;
        this.alGuardar = alGuardar;
        setIconImage(IconoRQ.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado(editando == null ? "Nuevo administrador" : "Editar usuario",
                editando == null ? "Crea una cuenta de administración con su cargo" : editando.getLogin() + " · " + editando.getTipo()), BorderLayout.NORTH);

        boolean esAdministrativo = editando == null || editando.getRol() == Rol.ADMINISTRADOR;
        DefaultComboBoxModel<Cargo> cargos = new DefaultComboBoxModel<>();
        if (editando != null && editando.esAdministradorPrincipal()) {
            cargos.addElement(Cargo.ADMINISTRADOR_PRINCIPAL);
        } else {
            for (Cargo c : Cargo.asignablesA(Rol.ADMINISTRADOR)) {
                cargos.addElement(c);
            }
        }
        cmbCargo.setModel(cargos);
        cmbCargo.setFont(Tema.fuente(Font.PLAIN, 13f));

        ValidacionUI.usuario(txtLogin, 20);
        ValidacionUI.soloLetras(txtNombre, 50);
        ValidacionUI.soloDigitos(txtCedula, 10);
        ValidacionUI.mostrarProvincia(txtCedula, lblProvincia);
        ValidacionUI.correo(txtEmail, 50);
        ValidacionUI.celular(txtTelefono);
        lblProvincia.setForeground(Tema.TEXTO_SUAVE);

        JLabel errLogin = ValidacionUI.etiquetaError();
        JLabel errNombre = ValidacionUI.etiquetaError();
        JLabel errCedula = ValidacionUI.etiquetaError();
        JLabel errClave = ValidacionUI.etiquetaError();
        JLabel errConfirmar = ValidacionUI.etiquetaError();
        JLabel errEmail = ValidacionUI.etiquetaError();
        JLabel errTelefono = ValidacionUI.etiquetaError();

        boolean nuevo = editando == null;
        JPanel f = Tema.panelFormulario();
        int fila = 0;
        fila = ValidacionUI.fila(f, fila, "Usuario *", txtLogin, errLogin);
        fila = ValidacionUI.fila(f, fila, "Nombre completo *", txtNombre, errNombre);
        fila = ValidacionUI.fila(f, fila, "Cédula *", txtCedula, errCedula);
        Tema.fila(f, fila++, "", lblProvincia);
        fila = ValidacionUI.fila(f, fila, nuevo ? "Correo *" : "Correo (opcional)", txtEmail, errEmail);
        fila = ValidacionUI.fila(f, fila, nuevo ? "Celular *" : "Celular (opcional)", txtTelefono, errTelefono);
        if (esAdministrativo) {
            Tema.fila(f, fila++, "Cargo *", cmbCargo);
        }
        fila = ValidacionUI.fila(f, fila, nuevo ? "Contraseña temporal *" : "Nueva contraseña", txtClave, errClave);
        Tema.fila(f, fila++, "", lblFortaleza);
        if (nuevo) {
            fila = ValidacionUI.fila(f, fila, "Confirmar contraseña *", txtConfirmar, errConfirmar);
        } else {
            Tema.fila(f, fila++, "Estado", cmbEstado);
        }
        Tema.vincularFortaleza(txtClave, lblFortaleza);

        reglas.agregar(txtLogin, errLogin, () -> Validador.errorLogin(txtLogin.getText()));
        reglas.agregar(txtNombre, errNombre, () -> Validador.errorNombre(txtNombre.getText()));
        reglas.agregar(txtCedula, errCedula, () -> Validador.errorCedula(txtCedula.getText()));
        reglas.agregar(txtEmail, errEmail, () -> nuevo ? Validador.errorEmailCuenta(txtEmail.getText()) : Validador.errorEmailOpcional(txtEmail.getText()));
        reglas.agregar(txtTelefono, errTelefono, () -> nuevo ? Validador.errorTelefonoCelular(txtTelefono.getText()) : Validador.errorTelefonoOpcional(txtTelefono.getText()));
        reglas.agregar(txtClave, errClave, () -> {
            String clave = new String(txtClave.getPassword());
            return !nuevo && clave.isEmpty() ? null : Validador.errorContrasena(clave);
        });
        if (nuevo) {
            reglas.agregar(txtConfirmar, errConfirmar, () -> new String(txtClave.getPassword()).equals(new String(txtConfirmar.getPassword()))
                    ? null : "Las contraseñas no coinciden");
        }

        JLabel nota = Tema.nota(540, "<html>Solo el administrador principal crea administradores. Los pasajeros y los conductores se registran solos desde "
                + "<b>Crear cuenta</b> (los conductores quedan pendientes hasta que revises sus documentos). "
                + (nuevo ? "Al ingresar por primera vez, el nuevo administrador cambiará la contraseña temporal y elegirá su pregunta de seguridad personal."
                : "Deja la contraseña vacía para no cambiarla.") + "</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);

        JButton guardar = Tema.boton("Guardar", true);
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
        centro.setBorder(BorderFactory.createEmptyBorder(14, 28, 14, 28));
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

        if (editando != null) {
            txtLogin.setText(editando.getLogin());
            txtNombre.setText(editando.getNombre());
            txtCedula.setText(editando.getCedula());
            txtEmail.setText(editando.getEmail());
            txtTelefono.setText(editando.getTelefono());
            cmbEstado.setSelectedItem(editando.getEstado());
            Cargo cargo = Cargo.desdeEtiqueta(editando.getCargo());
            if (cargo != null) {
                cmbCargo.setSelectedItem(cargo);
            }
            txtLogin.setEnabled(false);
            txtCedula.setEnabled(false);
            txtNombre.setEnabled(editando.getRol() != Rol.CONDUCTOR);
            cmbCargo.setEnabled(!editando.esAdministradorPrincipal());
            cmbEstado.setEnabled(!editando.esAdministradorPrincipal());
        }
        setPreferredSize(new Dimension(660, 700));
        pack();
        setLocationRelativeTo(padre);
    }

    private void guardar() {
        if (!reglas.validar()) {
            Tema.mostrarMensaje(lblMensaje, "Revisa los campos marcados en rojo.", false);
            return;
        }
        Cargo cargo = (Cargo) cmbCargo.getSelectedItem();
        String etiquetaCargo = cargo == null ? "" : cargo.getEtiqueta();
        ResultadoOperacion r;
        if (editando == null) {
            r = controlador.crearUsuarioAdministrativo(actor, Rol.ADMINISTRADOR.getEtiqueta(), etiquetaCargo, txtLogin.getText(), txtNombre.getText(),
                    txtCedula.getText(), new String(txtClave.getPassword()), txtEmail.getText(), txtTelefono.getText(), "", "", "");
        } else {
            r = controlador.actualizarUsuarioAdministrativo(actor, editando.getIdUsuario(), etiquetaCargo, txtNombre.getText(), txtEmail.getText(),
                    txtTelefono.getText(), (String) cmbEstado.getSelectedItem(), new String(txtClave.getPassword()));
        }
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        alGuardar.run();
        dispose();
    }
}
