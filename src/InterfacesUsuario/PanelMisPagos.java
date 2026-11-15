package InterfacesUsuario;

import control.ControladorFacturacion;
import control.ControladorLiquidaciones;
import control.ControladorTarjetas;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import objetosNegocio.Comprobante;
import objetosNegocio.Liquidacion;
import objetosNegocio.TarjetaGuardada;
import objetosNegocio.Usuario;

/** Pagos del conductor: su tarjeta de débito registrada y el 80 % que gana por cada viaje realizado. */
public class PanelMisPagos extends JPanel {
    private final ControladorLiquidaciones liquidaciones = new ControladorLiquidaciones();
    private final ControladorTarjetas tarjetas = new ControladorTarjetas();
    private final Usuario usuario;
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"Viaje", "Salida", "Ruta", "Pasajeros", "Recaudado", "Mi pago (80 %)", "Estado", "Llegada", "Pagado a", "Factura"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private final JLabel lblTarjeta = new JLabel(" ");
    private final JLabel lblTotales = Tema.mensaje();
    private List<Liquidacion> actuales = new ArrayList<>();

    public PanelMisPagos(Usuario usuario) {
        this.usuario = usuario;
        setLayout(new BorderLayout(0, 10));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        Tema.tabla(tabla);
        lblTarjeta.setFont(Tema.fuente(Font.BOLD, 14f));
        lblTarjeta.setForeground(Color.BLACK);
        JButton cambiar = Tema.boton("Cambiar tarjeta", false);
        cambiar.addActionListener(e -> new DialogoTarjetaCobro(javax.swing.SwingUtilities.getWindowAncestor(this), usuario, this::cargar).setVisible(true));
        JButton factura = Tema.boton("Ver factura del pago", true);
        factura.addActionListener(e -> verFactura());
        JButton pdf = Tema.boton("Descargar PDF", false);
        pdf.addActionListener(e -> descargarPdf());
        JButton actualizar = Tema.boton("Actualizar", false);
        actualizar.addActionListener(e -> cargar());
        JPanel arriba = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        arriba.setOpaque(false);
        arriba.add(Tema.etiqueta("Tarjeta de cobro:"));
        arriba.add(lblTarjeta);
        arriba.add(cambiar);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        botones.setOpaque(false);
        botones.add(actualizar);
        botones.add(factura);
        botones.add(pdf);
        JLabel regla = Tema.etiqueta("Ganas el 80 % de lo que pagaron los pasajeros por cada viaje. El pago se libera al llegar el bus a destino y finanzas lo deposita en tu tarjeta.");
        regla.setForeground(Tema.TEXTO_SUAVE);
        JPanel sur = new JPanel(new BorderLayout());
        sur.setOpaque(false);
        sur.add(lblTotales, BorderLayout.NORTH);
        sur.add(regla, BorderLayout.WEST);
        sur.add(botones, BorderLayout.EAST);
        add(arriba, BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
        cargar();
    }

    @SuppressWarnings("unchecked")
    public final void cargar() {
        TarjetaGuardada t = tarjetas.obtenerTarjetaCobro(usuario.getIdPerfil());
        lblTarjeta.setText(t == null ? "NO REGISTRADA (no podrán pagarte)" : t.resumen() + " · vence " + t.getVence());
        lblTarjeta.setForeground(t == null ? Tema.ERROR : Color.BLACK);
        modelo.setRowCount(0);
        ResultadoOperacion r = liquidaciones.listarMias(usuario);
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblTotales, r.getMensaje(), false);
            return;
        }
        actuales = (List<Liquidacion>) r.getDatos();
        double pagado = 0, pendiente = 0;
        for (Liquidacion l : actuales) {
            boolean paga = Liquidacion.PAGADA.equals(l.getEstado());
            String estado = paga ? "PAGADO" : Liquidacion.PROGRAMADA.equals(l.getEstado()) ? "PROGRAMADO (en espera)" : "PENDIENTE DE PAGO";
            if (paga) pagado += l.getMonto(); else pendiente += l.getMonto();
            modelo.addRow(new Object[]{l.getIdViaje(), l.getFechaViaje(), l.getRuta(), l.getPasajeros(), String.format("$%.2f", l.getRecaudado()),
                    String.format("$%.2f", l.getMonto()), estado, l.getLlegadaViaje().isEmpty() ? "—" : l.getLlegadaViaje(), l.getTarjeta(), l.getNumeroFactura().isEmpty() ? "—" : l.getNumeroFactura()});
        }
        Tema.mostrarMensaje(lblTotales, String.format("Ya te pagaron: $%.2f · Por cobrar: $%.2f", pagado, pendiente), true);
    }

    private void verFactura() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            Tema.info(this, "Selecciona un pago.");
            return;
        }
        String viaje = String.valueOf(modelo.getValueAt(fila, 0));
        Liquidacion l = actuales.stream().filter(x -> x.getIdViaje().equals(viaje)).findFirst().orElse(null);
        Comprobante c = l == null ? null : new ControladorFacturacion().buscarPorReferencia(Comprobante.LIQUIDACION, l.getIdLiquidacion());
        if (c == null) {
            Tema.info(this, "Este pago aún no tiene factura: se emite cuando finanzas lo paga.");
            return;
        }
        new DialogoFactura(javax.swing.SwingUtilities.getWindowAncestor(this), c).setVisible(true);
    }

    private void descargarPdf() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            Tema.info(this, "Selecciona un pago.");
            return;
        }
        String viaje = String.valueOf(modelo.getValueAt(fila, 0));
        Liquidacion l = actuales.stream().filter(x -> x.getIdViaje().equals(viaje)).findFirst().orElse(null);
        if (l != null) {
            Descargas.guardarPdf(this, "Descargar pago", control.ComprobantesPdf.nombreSugerido("Pago", l.getIdLiquidacion()),
                    destino -> new control.ComprobantesPdf().pagoConductor(destino, l));
        }
    }
}
