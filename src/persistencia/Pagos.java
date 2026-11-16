package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import java.util.List;
import objetosNegocio.Factura;
import objetosNegocio.Pago;
import objetosNegocio.Usuario;

/** Pagos en la tabla PAGOS de Oracle. */
public class Pagos {
    private static final String COLUMNAS = "ID_PAGO, ID_USUARIO, ID_FACTURA, MONTO, FECHA_PAGO, METODO";

    public Pagos(String ignorado) {
    }

    private static Pago mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Pago p = new Pago();
        p.setIdPago(Bd.texto(rs, "ID_PAGO"));
        p.setUsuario(new Usuario(Bd.texto(rs, "ID_USUARIO")));
        p.setFactura(new Factura(Bd.texto(rs, "ID_FACTURA")));
        p.setMonto(rs.getDouble("MONTO"));
        p.setFechaPago(Bd.fecha(rs, "FECHA_PAGO"));
        p.setMetodo(Bd.texto(rs, "METODO"));
        return p;
    }

    private static String idUsuario(Pago p) {
        return p.getUsuario() != null ? p.getUsuario().getIdUsuario() : "";
    }

    private static String idFactura(Pago p) {
        return p.getFactura() != null ? p.getFactura().getIdFactura() : "";
    }

    public Pago obten(Pago pago) throws PersistenciaException {
        if (pago == null || pago.getIdPago() == null || pago.getIdPago().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM PAGOS WHERE ID_PAGO = ?", Pagos::mapear, pago.getIdPago());
    }

    public void agrega(Pago p) throws PersistenciaException {
        if (p.getIdPago() != null && !p.getIdPago().isEmpty()) {
            Bd.actualizar("INSERT INTO PAGOS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?)",
                    p.getIdPago(), idUsuario(p), idFactura(p), p.getMonto(), p.getFechaPago(), p.getMetodo());
            return;
        }
        Bd.insertarConCodigo("PAGOS", "ID_PAGO", "PAG", 3, p::setIdPago, "INSERT INTO PAGOS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?)",
                id -> new Object[]{id, idUsuario(p), idFactura(p), p.getMonto(), p.getFechaPago(), p.getMetodo()});
    }

    public void actualiza(Pago p) throws PersistenciaException {
        int n = Bd.actualizar("UPDATE PAGOS SET ID_USUARIO=?, ID_FACTURA=?, MONTO=?, FECHA_PAGO=?, METODO=? WHERE ID_PAGO=?",
                idUsuario(p), idFactura(p), p.getMonto(), p.getFechaPago(), p.getMetodo(), p.getIdPago());
        if (n == 0) {
            throw new PersistenciaException("El pago no existe");
        }
    }

    public void elimina(Pago p) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM PAGOS WHERE ID_PAGO = ?", p.getIdPago()) == 0) {
            throw new PersistenciaException("El pago no existe");
        }
    }

    public List<Pago> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM PAGOS ORDER BY ID_PAGO", Pagos::mapear);
    }

    public List<Pago> listaPorUsuario(String idUsuario) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM PAGOS WHERE ID_USUARIO = ? ORDER BY ID_PAGO", Pagos::mapear, idUsuario);
    }

    public List<Pago> listaPorMetodo(String metodo) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM PAGOS WHERE METODO = ? ORDER BY ID_PAGO", Pagos::mapear, metodo);
    }
}
