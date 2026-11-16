package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import java.util.List;
import objetosNegocio.Novedad;

/** Novedades, quejas y sugerencias en la tabla NOVEDADES de Oracle. */
public class Novedades {
    private static final String COLUMNAS = "ID_NOVEDAD, ID_CONDUCTOR, ID_VIAJE, TIPO, ASUNTO, MENSAJE, FECHA, HORA, ESTADO, RESPUESTA, ID_REVISOR";

    public Novedades(String ignorado) {
    }

    private static Novedad mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Novedad n = new Novedad();
        n.setIdNovedad(Bd.texto(rs, "ID_NOVEDAD"));
        n.setIdConductor(Bd.texto(rs, "ID_CONDUCTOR"));
        n.setIdViaje(Bd.texto(rs, "ID_VIAJE"));
        n.setTipo(Bd.texto(rs, "TIPO"));
        n.setAsunto(Bd.texto(rs, "ASUNTO"));
        n.setMensaje(Bd.texto(rs, "MENSAJE"));
        n.setFecha(Bd.fecha(rs, "FECHA"));
        n.setHora(Bd.texto(rs, "HORA"));
        n.setEstado(Bd.texto(rs, "ESTADO"));
        n.setRespuesta(Bd.texto(rs, "RESPUESTA"));
        n.setIdRevisor(Bd.texto(rs, "ID_REVISOR"));
        return n;
    }

    public synchronized void agrega(Novedad n) throws PersistenciaException {
        Bd.insertarConCodigo("NOVEDADES", "ID_NOVEDAD", "NOV", 5, n::setIdNovedad,
                "INSERT INTO NOVEDADES (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id -> new Object[]{id, n.getIdConductor(), n.getIdViaje(), n.getTipo(), n.getAsunto(), n.getMensaje(), n.getFecha(), n.getHora(),
                        n.getEstado(), n.getRespuesta(), n.getIdRevisor()});
    }

    public synchronized void actualiza(Novedad n) throws PersistenciaException {
        int filas = Bd.actualizar("UPDATE NOVEDADES SET ID_CONDUCTOR=?, ID_VIAJE=?, TIPO=?, ASUNTO=?, MENSAJE=?, FECHA=?, HORA=?, ESTADO=?, RESPUESTA=?, ID_REVISOR=? WHERE ID_NOVEDAD=?",
                n.getIdConductor(), n.getIdViaje(), n.getTipo(), n.getAsunto(), n.getMensaje(), n.getFecha(), n.getHora(), n.getEstado(),
                n.getRespuesta(), n.getIdRevisor(), n.getIdNovedad());
        if (filas == 0) {
            throw new PersistenciaException("Novedad no encontrada: " + n.getIdNovedad());
        }
    }

    public synchronized List<Novedad> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM NOVEDADES ORDER BY ID_NOVEDAD", Novedades::mapear);
    }

    public Novedad obten(String id) throws PersistenciaException {
        return Bd.uno("SELECT " + COLUMNAS + " FROM NOVEDADES WHERE ID_NOVEDAD = ?", Novedades::mapear, id);
    }
}
