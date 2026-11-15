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
            new String[]{"ID", "Viaje", "Salida", "Ruta", "Conductor", "Pasajeros", "Recaudado", "Pago 80 %", "Estado", "Llegada", "Tarjeta de cobro", "Factura"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private final JComboBox<String> cmbEstado = new JComboBox<>(new String[]{"Por pagar (pendientes)", "Pagados", "Programados (viaje aceptado)", "Todos"});
    private final JLabel lblEstado = Tema.mensaje();
    private List<Liquidacion> actuales = new ArrayList<>();

    public PanelPagosConductores(Usuario usuario) {
        this.usuario = usuario;
        setLayout(new BorderLayout(0, 8));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        Tema.tabla(tabla);
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        int[] anchos = {80, 70, 130, 170, 170, 80, 90, 90, 100, 130, 200, 150};
        for (int i = 0; i < anchos.length && i < tabla.getColumnCount(); i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        JButton btnPagar = Tema.boton("Pagar al conductor", true);
        btnPagar.setEnabled(Permisos.puedeEditar(usuario, Permisos.Modulo.PAGOS));
        btnPagar.addActionListener(e -> pagar());
        JButton btnFactura = Tema.boton("Ver factura del pago", false);
        btnFactura.addActionListener(e -> verFactura());
        JButton btnPdf = Tema.boton("Descargar PDF", false);
        btnPdf.addActionListener(e -> descargarPdf());
        JButton btnActualizar = Tema.boton("Actualizar", false);
        btnActualizar.addActionListener(e -> cargar());
        cmbEstado.setFont(Tema.fuente(Font.PLAIN, 13f));
        cmbEstado.addActionListener(e -> cargar());
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        barra.setOpaque(false);
        barra.add(btnPagar);
        barra.add(btnFactura);
        barra.add(btnPdf);
        barra.add(btnActualizar);
        barra.add(Tema.etiqueta("Mostrar:"));
        barra.add(cmbEstado);
        JLabel nota = Tema.nota(900, "<html>Cuando el conductor <b>acepta</b> un viaje aparece aquí como <b>PROGRAMADA</b> (aún no se paga). Al llegar el bus a destino "
                + "pasa a <b>PENDIENTE</b> y recién entonces se puede pagar el <b>80 %</b> de lo recaudado (el 20 % es margen de la plataforma). "
                + "Si el conductor cancela con aprobación del admin, sale de la lista.</html>");
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
        double pendiente = 0, pagado = 0, programado = 0;
        for (Liquidacion l : (List<Liquidacion>) r.getDatos()) {
            String e = l.getEstado();
            if (Liquidacion.CANCELADA.equals(e)) {
                continue;
            }
            if (Liquidacion.PAGADA.equals(e)) pagado += l.getMonto();
            else if (Liquidacion.PROGRAMADA.equals(e)) programado += l.getMonto();
            else pendiente += l.getMonto();
            boolean mostrar = filtro == 3 || (filtro == 0 && Liquidacion.PENDIENTE.equals(e))
                    || (filtro == 1 && Liquidacion.PAGADA.equals(e)) || (filtro == 2 && Liquidacion.PROGRAMADA.equals(e));
            if (!mostrar) {
                continue;
            }
            actuales.add(l);
            modelo.addRow(new Object[]{l.getIdLiquidacion(), l.getIdViaje(), l.getFechaViaje(), l.getRuta(), l.getNombreConductor(), l.getPasajeros(),
                    String.format("$%.2f", l.getRecaudado()), String.format("$%.2f", l.getMonto()), e,
                    l.getLlegadaViaje().isEmpty() ? "—" : l.getLlegadaViaje(), l.getTarjeta(),
                    l.getNumeroFactura().isEmpty() ? "—" : l.getNumeroFactura()});
        }
        Tema.mostrarMensaje(lblEstado, String.format("Por pagar: $%.2f · Programado (esperando llegada): $%.2f · Ya pagado: $%.2f", pendiente, programado, pagado), true);
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
        if (Liquidacion.PROGRAMADA.equals(l.getEstado())) {
            Tema.info(this, "El viaje aún no termina. El pago se libera cuando el bus llegue a destino (" + l.getLlegadaViaje() + ").");
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

    private void descargarPdf() {
        Liquidacion l = seleccionada();
        if (l != null) {
            Descargas.guardarPdf(this, "Descargar pago", control.ComprobantesPdf.nombreSugerido("Pago", l.getIdLiquidacion()),
                    destino -> new control.ComprobantesPdf().pagoConductor(destino, l));
        }
    }
}
