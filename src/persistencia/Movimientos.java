package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import java.util.List;
import objetosNegocio.Movimiento;

/** Libro de movimientos de la billetera (tabla MOVIMIENTOS). El saldo siempre se calcula a partir de este libro. */
public class Movimientos {
    private static final String COLUMNAS = "ID_MOVIMIENTO, ID_PASAJERO, TIPO, MONTO, METODO, ESTADO, REFERENCIA, FECHA, HORA, ID_REVISOR, MOTIVO";

    public Movimientos(String ignorado) {
    }

    private static Movimiento mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Movimiento m = new Movimiento();
        m.setIdMovimiento(Bd.texto(rs, "ID_MOVIMIENTO"));
        m.setIdPasajero(Bd.texto(rs, "ID_PASAJERO"));
        m.setTipo(Bd.texto(rs, "TIPO"));
        m.setMonto(rs.getDouble("MONTO"));
        m.setMetodo(Bd.texto(rs, "METODO"));
        m.setEstado(Bd.texto(rs, "ESTADO"));
        m.setReferencia(Bd.texto(rs, "REFERENCIA"));
        m.setFecha(Bd.fecha(rs, "FECHA"));
        m.setHora(Bd.texto(rs, "HORA"));
        m.setIdRevisor(Bd.texto(rs, "ID_REVISOR"));
        m.setMotivo(Bd.texto(rs, "MOTIVO"));
        return m;
    }

    public synchronized void agrega(Movimiento m) throws PersistenciaException {
        Bd.insertarConCodigo("MOVIMIENTOS", "ID_MOVIMIENTO", "MOV", 5, m::setIdMovimiento,
                "INSERT INTO MOVIMIENTOS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id -> new Object[]{id, m.getIdPasajero(), m.getTipo(), m.getMonto(), m.getMetodo(), m.getEstado(), m.getReferencia(),
                        m.getFecha(), m.getHora(), m.getIdRevisor(), m.getMotivo()});
    }

    public synchronized void actualiza(Movimiento m) throws PersistenciaException {
        int n = Bd.actualizar("UPDATE MOVIMIENTOS SET ID_PASAJERO=?, TIPO=?, MONTO=?, METODO=?, ESTADO=?, REFERENCIA=?, FECHA=?, HORA=?, ID_REVISOR=?, MOTIVO=? WHERE ID_MOVIMIENTO=?",
                m.getIdPasajero(), m.getTipo(), m.getMonto(), m.getMetodo(), m.getEstado(), m.getReferencia(), m.getFecha(), m.getHora(),
                m.getIdRevisor(), m.getMotivo(), m.getIdMovimiento());
        if (n == 0) {
            throw new PersistenciaException("Movimiento no encontrado: " + m.getIdMovimiento());
        }
    }

    public synchronized List<Movimiento> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM MOVIMIENTOS ORDER BY ID_MOVIMIENTO", Movimientos::mapear);
    }

    public Movimiento obten(String idMovimiento) throws PersistenciaException {
        return Bd.uno("SELECT " + COLUMNAS + " FROM MOVIMIENTOS WHERE ID_MOVIMIENTO = ?", Movimientos::mapear, idMovimiento);
    }

    public List<Movimiento> listaPorPasajero(String idPasajero) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM MOVIMIENTOS WHERE ID_PASAJERO = ? ORDER BY ID_MOVIMIENTO", Movimientos::mapear, idPasajero);
    }
}
