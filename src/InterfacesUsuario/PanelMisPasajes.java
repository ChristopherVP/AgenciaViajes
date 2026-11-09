package InterfacesUsuario;

import control.ControladorCompras;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import objetosNegocio.Reserva;
import objetosNegocio.Usuario;
import objetosNegocio.Viaje;

/** Pasajes comprados por el pasajero. Son definitivos: no se cancelan ni se reembolsan. Se puede ver la ruta y la factura de cada uno. */
public class PanelMisPasajes extends JPanel {
    private final ControladorCompras compras = new ControladorCompras();
    private final Usuario usuario;
    private final Runnable alCambiar;
    private final DefaultTableModel modelo = new DefaultTableModel(new String[]{"Reserva", "Ruta", "Salida", "Asientos", "Total", "Estado"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private final JLabel lblMensaje = Tema.mensaje();
    private List<Reserva> reservas = new java.util.ArrayList<>();

    public PanelMisPasajes(Usuario usuario, Runnable alCambiar) {
        this.usuario = usuario;
        this.alCambiar = alCambiar;
        setLayout(new BorderLayout(0, 10));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        Tema.tabla(tabla);
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.getViewport().setBackground(Color.WHITE);
        add(scroll, BorderLayout.CENTER);

        javax.swing.JButton verRuta = Tema.boton("Ver ruta en el mapa", false);
        verRuta.addActionListener(e -> verRuta());
        javax.swing.JButton verFactura = Tema.boton("Ver factura", true);
        verFactura.addActionListener(e -> verFactura());
        javax.swing.JButton actualizar = Tema.boton("Actualizar", false);
        actualizar.addActionListener(e -> recargar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        botones.setOpaque(false);
        botones.add(actualizar);
        botones.add(verRuta);
        botones.add(verFactura);
        JLabel regla = Tema.etiqueta("Los pasajes comprados son definitivos: no se cancelan ni se devuelve el dinero.");
        regla.setForeground(Tema.TEXTO_SUAVE);
        JPanel sur = new JPanel(new BorderLayout());
        sur.setOpaque(false);
        sur.add(regla, BorderLayout.WEST);
        sur.add(botones, BorderLayout.EAST);
        JPanel pie = new JPanel(new BorderLayout());
        pie.setOpaque(false);
        pie.add(lblMensaje, BorderLayout.NORTH);
        pie.add(sur, BorderLayout.CENTER);
        add(pie, BorderLayout.SOUTH);
        recargar();
    }

    public final void recargar() {
        modelo.setRowCount(0);
        ResultadoOperacion r = compras.misPasajes(usuario.getIdPerfil());
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        @SuppressWarnings("unchecked")
        List<Reserva> lista = (List<Reserva>) r.getDatos();
        reservas = lista;
        for (Reserva x : reservas) {
            Viaje v = x.getViaje();
            String salida = "";
            if (v != null && v.getFechaSalida() != null) {
                salida = String.format("%02d/%02d/%04d %s", v.getFechaSalida().getDia(), v.getFechaSalida().getMes(), v.getFechaSalida().getAnio(), v.getHoraSalida() == null ? "" : v.getHoraSalida());
            }
            modelo.addRow(new Object[]{x.getIdReserva(), compras.describirRuta(v), salida, x.getCantidadAsientos(), Tema.dinero(x.getPrecioTotal()), x.getEstado()});
        }
        lblMensaje.setText(reservas.isEmpty() ? "Aún no has comprado pasajes." : " ");
    }

    private Reserva seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            Tema.error(this, "Selecciona uno de tus pasajes.");
            return null;
        }
        String id = String.valueOf(modelo.getValueAt(fila, 0));
        return reservas.stream().filter(r -> r.getIdReserva().equals(id)).findFirst().orElse(null);
    }

    private void verRuta() {
        Reserva r = seleccionada();
        if (r == null || r.getViaje() == null) {
            return;
        }
        try {
            objetosNegocio.Ruta ruta = fachadas.FachadaArchivos.getInstance().obten(r.getViaje().getRuta());
            if (ruta != null) {
                new DialogoMapaRuta(javax.swing.SwingUtilities.getWindowAncestor(this), ruta, "").setVisible(true);
            }
        } catch (Exception e) {
            Tema.error(this, "No se pudo cargar la ruta: " + e.getMessage());
        }
    }

    private void verFactura() {
        Reserva r = seleccionada();
        if (r == null) {
            return;
        }
        objetosNegocio.Comprobante c = new control.ControladorFacturacion().buscarPorReferencia(objetosNegocio.Comprobante.PASAJE, r.getIdReserva());
        if (c == null) {
            Tema.info(this, "Este pasaje no tiene factura registrada.");
            return;
        }
        new DialogoFactura(javax.swing.SwingUtilities.getWindowAncestor(this), c).setVisible(true);
    }
}
