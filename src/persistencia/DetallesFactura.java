package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import java.util.List;
import objetosNegocio.DetalleFactura;

/** Líneas de cada factura en la tabla DETALLES_FACTURA de Oracle. */
public class DetallesFactura {
    private static final String COLUMNAS = "ID_DETALLE, ID_FACTURA, DESCRIPCION, CANTIDAD, PRECIO_UNITARIO, SUBTOTAL";

    public DetallesFactura(String ignorado) {
    }

    private static DetalleFactura mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        DetalleFactura d = new DetalleFactura();
        d.setIdDetalle(Bd.texto(rs, "ID_DETALLE"));
        d.setIdFactura(Bd.texto(rs, "ID_FACTURA"));
        d.setDescripcion(Bd.texto(rs, "DESCRIPCION"));
        d.setCantidad(rs.getInt("CANTIDAD"));
        d.setPrecioUnitario(rs.getDouble("PRECIO_UNITARIO"));
        d.setSubtotal(rs.getDouble("SUBTOTAL"));
        return d;
    }

    public void agrega(DetalleFactura d) throws PersistenciaException {
        Bd.insertarConCodigo("DETALLES_FACTURA", "ID_DETALLE", "DET", 5, d::setIdDetalle,
                "INSERT INTO DETALLES_FACTURA (" + COLUMNAS + ") VALUES (?,?,?,?,?,?)",
                id -> new Object[]{id, d.getIdFactura(), d.getDescripcion(), d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal()});
    }

    public List<DetalleFactura> listaPorFactura(String idFactura) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM DETALLES_FACTURA WHERE ID_FACTURA = ? ORDER BY ID_DETALLE", DetallesFactura::mapear,
                idFactura == null ? "" : idFactura.trim());
    }

    public void eliminarPorFactura(String idFactura) throws PersistenciaException {
        Bd.actualizar("DELETE FROM DETALLES_FACTURA WHERE ID_FACTURA = ?", idFactura == null ? "" : idFactura.trim());
    }
}
