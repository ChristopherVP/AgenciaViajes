package InterfacesUsuario;

import control.ControladorFacturacion;
import control.ControladorLiquidaciones;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import objetosNegocio.Comprobante;
import objetosNegocio.Liquidacion;
import objetosNegocio.Usuario;
import objetosServicio.Permisos;

/**
 * Pagos a conductores. Cada viaje que el conductor notifica como realizado genera un pago del 80 % de lo recaudado en pasajes.
 * El personal de finanzas lo paga a la tarjeta de débito registrada del conductor y se emite la factura del pago.
 */
public class PanelPagosConductores extends JPanel {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ControladorLiquidaciones controlador = new ControladorLiquidaciones();
    private final Usuario usuario;
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"ID", "Viaje", "Salida", "Ruta", "Conductor", "Pasajeros", "Recaudado", "Pago 80 %", "Estado", "Tarjeta de cobro", "Factura"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private final JComboBox<String> cmbEstado = new JComboBox<>(new String[]{"Pendientes de pago", "Pagados", "Todos"});
    private final JLabel lblEstado = Tema.mensaje();
    private List<Liquidacion> actuales = new ArrayList<>();

    public PanelPagosConductores(Usuario usuario) {
        this.usuario = usuario;
        setLayout(new BorderLayout(0, 8));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        Tema.tabla(tabla);
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        JButton btnPagar = Tema.boton("Pagar al conductor", true);
        btnPagar.setEnabled(Permisos.puedeEditar(usuario, Permisos.Modulo.PAGOS));
        btnPagar.addActionListener(e -> pagar());
        JButton btnFactura = Tema.boton("Ver factura del pago", false);
        btnFactura.addActionListener(e -> verFactura());
        JButton btnActualizar = Tema.boton("Actualizar", false);
        btnActualizar.addActionListener(e -> cargar());
        cmbEstado.setFont(Tema.fuente(Font.PLAIN, 13f));
        cmbEstado.addActionListener(e -> cargar());
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        barra.setOpaque(false);
        barra.add(btnPagar);
        barra.add(btnFactura);
        barra.add(btnActualizar);
        barra.add(Tema.etiqueta("Mostrar:"));
        barra.add(cmbEstado);
        JLabel nota = Tema.nota(900, "<html>El conductor notifica que el viaje se realizó con éxito y aquí aparece su pago: el <b>80 %</b> de lo que pagaron los pasajeros "
                + "(el 20 % es el margen de la plataforma). Solo finanzas puede pagar.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);
        JPanel arriba = new JPanel(new BorderLayout());
        arriba.setOpaque(false);
        arriba.add(barra, BorderLayout.NORTH);
        arriba.add(nota, BorderLayout.SOUTH);
        add(arriba, BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(lblEstado, BorderLayout.SOUTH);
        cargar();
    }

    @SuppressWarnings("unchecked")
    public void cargar() {
        modelo.setRowCount(0);
        ResultadoOperacion r = controlador.listarParaAdministrador(usuario);
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblEstado, r.getMensaje(), false);
            return;
        }
        int filtro = cmbEstado.getSelectedIndex();
        actuales = new ArrayList<>();
        double pendiente = 0, pagado = 0;
        for (Liquidacion l : (List<Liquidacion>) r.getDatos()) {
            boolean paga = Liquidacion.PAGADA.equals(l.getEstado());
            if (paga) pagado += l.getMonto(); else pendiente += l.getMonto();
            if ((filtro == 0 && paga) || (filtro == 1 && !paga)) {
                continue;
            }
            actuales.add(l);
            modelo.addRow(new Object[]{l.getIdLiquidacion(), l.getIdViaje(), l.getFechaViaje(), l.getRuta(), l.getNombreConductor(), l.getPasajeros(),
                    String.format("$%.2f", l.getRecaudado()), String.format("$%.2f", l.getMonto()), paga ? "PAGADA" : "PENDIENTE", l.getTarjeta(),
                    l.getNumeroFactura().isEmpty() ? "—" : l.getNumeroFactura()});
        }
        Tema.mostrarMensaje(lblEstado, String.format("Pendiente de pago: $%.2f · Ya pagado: $%.2f", pendiente, pagado), true);
    }

    private Liquidacion seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            Tema.info(this, "Selecciona un pago de la lista.");
            return null;
        }
        String id = String.valueOf(modelo.getValueAt(fila, 0));
        return actuales.stream().filter(l -> l.getIdLiquidacion().equals(id)).findFirst().orElse(null);
    }

    private void pagar() {
        Liquidacion l = seleccionada();
        if (l == null) {
            return;
        }
        if (Liquidacion.PAGADA.equals(l.getEstado())) {
            Tema.info(this, "Este pago ya fue realizado.");
            return;
        }
        String mensaje = String.format("¿Pagar $%.2f a %s?\n\nViaje %s (%s)\nPasajeros: %d · Recaudado: $%.2f\nSe depositará en: %s",
                l.getMonto(), l.getNombreConductor(), l.getIdViaje(), l.getRuta(), l.getPasajeros(), l.getRecaudado(), l.getTarjeta());
        if (JOptionPane.showConfirmDialog(this, mensaje, "Confirmar pago", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        ResultadoOperacion r = controlador.pagar(usuario, l.getIdLiquidacion());
        if (!r.isExito()) {
            Tema.error(this, r.getMensaje());
            return;
        }
        Tema.info(this, r.getMensaje());
        cargar();
    }

    private void verFactura() {
        Liquidacion l = seleccionada();
        if (l == null) {
            return;
        }
        Comprobante c = new ControladorFacturacion().buscarPorReferencia(Comprobante.LIQUIDACION, l.getIdLiquidacion());
        if (c == null) {
            Tema.info(this, "Este pago aún no tiene factura: se emite al pagar.");
            return;
        }
        new DialogoFactura(javax.swing.SwingUtilities.getWindowAncestor(this), c).setVisible(true);
    }
}
