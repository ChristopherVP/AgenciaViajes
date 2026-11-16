package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import java.util.ArrayList;
import java.util.List;
import objetosNegocio.Asiento;

/** Asientos de cada viaje en la tabla ASIENTOS de Oracle (se borran solos si se borra el viaje). */
public class Asientos {
    private static final String COLUMNAS = "ID_ASIENTO, NUMERO_ASIENTO, ESTADO, ID_VIAJE, ID_RESERVA, UBICACION, LIBERADO_EN";
    private static final String ORDEN = " ORDER BY ID_VIAJE, NUMERO_ASIENTO";

    public Asientos(String ignorado) {
    }

    private static Asiento mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Asiento a = new Asiento();
        a.setIdAsiento(Bd.texto(rs, "ID_ASIENTO"));
        a.setNumeroAsiento(rs.getInt("NUMERO_ASIENTO"));
        a.setEstado(Bd.texto(rs, "ESTADO"));
        a.setIdViaje(Bd.texto(rs, "ID_VIAJE"));
        a.setIdReserva(Bd.texto(rs, "ID_RESERVA"));
        a.setUbicacion(Bd.texto(rs, "UBICACION"));
        a.setLiberadoEn(Bd.texto(rs, "LIBERADO_EN"));
        return a;
    }

    private static Object[] valores(Asiento a) {
        return new Object[]{a.getIdAsiento(), a.getNumeroAsiento(), a.getEstado(), a.getIdViaje(), a.getIdReserva(), a.getUbicacion(), a.getLiberadoEn()};
    }

    public Asiento obten(Asiento asiento) throws PersistenciaException {
        if (asiento == null || asiento.getIdAsiento() == null || asiento.getIdAsiento().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM ASIENTOS WHERE ID_ASIENTO = ?", Asientos::mapear, asiento.getIdAsiento());
    }

    public void agrega(Asiento a) throws PersistenciaException {
        Bd.actualizar("INSERT INTO ASIENTOS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?)", valores(a));
    }

    /** Crea todos los asientos de un viaje en una sola operación (rápido: un solo viaje a la base de datos). */
    public void agregaLote(List<Asiento> asientos) throws PersistenciaException {
        if (asientos == null || asientos.isEmpty()) {
            return;
        }
        List<Object[]> filas = new ArrayList<>();
        for (Asiento a : asientos) {
            filas.add(valores(a));
        }
        Bd.transaccion(c -> {
            Bd.insertarLote(c, "INSERT INTO ASIENTOS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?)", filas);
            return null;
        });
    }

    public void actualiza(Asiento a) throws PersistenciaException {
        int n = Bd.actualizar("UPDATE ASIENTOS SET NUMERO_ASIENTO=?, ESTADO=?, ID_VIAJE=?, ID_RESERVA=?, UBICACION=?, LIBERADO_EN=? WHERE ID_ASIENTO=?",
                a.getNumeroAsiento(), a.getEstado(), a.getIdViaje(), a.getIdReserva(), a.getUbicacion(), a.getLiberadoEn(), a.getIdAsiento());
        if (n == 0) {
            throw new PersistenciaException("El asiento no existe");
        }
    }

    public void elimina(Asiento a) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM ASIENTOS WHERE ID_ASIENTO = ?", a.getIdAsiento()) == 0) {
            throw new PersistenciaException("El asiento no existe");
        }
    }

    public List<Asiento> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM ASIENTOS" + ORDEN, Asientos::mapear);
    }

    public List<Asiento> listaPorViaje(String idViaje) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM ASIENTOS WHERE ID_VIAJE = ?" + ORDEN, Asientos::mapear, idViaje);
    }

    public List<Asiento> listaPorEstado(String estado) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM ASIENTOS WHERE ESTADO = ?" + ORDEN, Asientos::mapear, estado);
    }

    public List<Asiento> listaDisponiblesPorViaje(String idViaje) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM ASIENTOS WHERE ID_VIAJE = ? AND ESTADO = 'Disponible'" + ORDEN, Asientos::mapear, idViaje);
    }

    public Asiento obtenPorViajeYNumero(String idViaje, int numeroAsiento) throws PersistenciaException {
        return Bd.uno("SELECT " + COLUMNAS + " FROM ASIENTOS WHERE ID_VIAJE = ? AND NUMERO_ASIENTO = ?", Asientos::mapear, idViaje, numeroAsiento);
    }
}
