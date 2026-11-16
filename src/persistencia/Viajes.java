package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import excepciones.Validador;
import java.util.List;
import objetosNegocio.Bus;
import objetosNegocio.Conductor;
import objetosNegocio.Empresa;
import objetosNegocio.Ruta;
import objetosNegocio.Viaje;

/** Viajes en la tabla VIAJES de Oracle. */
public class Viajes {
    private static final String COLUMNAS = "ID_VIAJE, ID_BUS, ID_RUTA, FECHA_SALIDA, FECHA_LLEGADA, ASIENTOS_DISPONIBLES, ESTADO, ID_EMPRESA, "
            + "HORA_SALIDA, HORA_LLEGADA, PRECIO, ID_CONDUCTOR1, ID_CONDUCTOR2";

    public Viajes(String ignorado) {
    }

    private static Viaje mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Viaje v = new Viaje();
        v.setIdViaje(Bd.texto(rs, "ID_VIAJE"));
        v.setBus(new Bus(Bd.texto(rs, "ID_BUS")));
        v.setRuta(new Ruta(Bd.texto(rs, "ID_RUTA")));
        v.setFechaSalida(Bd.fecha(rs, "FECHA_SALIDA"));
        v.setFechaLlegada(Bd.fecha(rs, "FECHA_LLEGADA"));
        v.setAsientosDisponibles(rs.getInt("ASIENTOS_DISPONIBLES"));
        v.setEstado(Bd.texto(rs, "ESTADO"));
        v.setEmpresa(new Empresa(Bd.texto(rs, "ID_EMPRESA")));
        v.setHoraSalida(Bd.texto(rs, "HORA_SALIDA"));
        v.setHoraLlegada(Bd.texto(rs, "HORA_LLEGADA"));
        v.setPrecio(rs.getDouble("PRECIO"));
        v.setConductor1(new Conductor(Bd.texto(rs, "ID_CONDUCTOR1")));
        v.setConductor2(new Conductor(Bd.texto(rs, "ID_CONDUCTOR2")));
        return v;
    }

    private static String idConductor(Conductor c) {
        return c != null && c.getIdConductor() != null ? c.getIdConductor() : "";
    }

    private static Object[] valores(String id, Viaje v) {
        return new Object[]{id, v.getBus().getIdBus(), v.getRuta().getIdRuta(), v.getFechaSalida(), v.getFechaLlegada(), v.getAsientosDisponibles(),
                v.getEstado(), v.getEmpresa().getIdEmpresa(), v.getHoraSalida(), v.getHoraLlegada(), v.getPrecio(),
                idConductor(v.getConductor1()), idConductor(v.getConductor2())};
    }

    private void validar(Viaje viaje) throws PersistenciaException {
        if (!Validador.validarNumeroPositivo(viaje.getAsientosDisponibles())) {
            throw new PersistenciaException(Validador.getMensajeErrorNumeroPositivo("Los asientos disponibles"));
        } else if (!Validador.validarNoVacio(viaje.getEstado())) {
            throw new PersistenciaException("El estado no puede estar vacío");
        } else if (viaje.getBus() == null || !Validador.validarNoVacio(viaje.getBus().getIdBus())) {
            throw new PersistenciaException("Debe asignar un bus al viaje");
        } else if (viaje.getRuta() == null || !Validador.validarNoVacio(viaje.getRuta().getIdRuta())) {
            throw new PersistenciaException("Debe asignar una ruta al viaje");
        } else if (viaje.getEmpresa() == null || !Validador.validarNoVacio(viaje.getEmpresa().getIdEmpresa())) {
            throw new PersistenciaException("Debe asignar una empresa al viaje");
        }
    }

    public Viaje obten(Viaje viaje) throws PersistenciaException {
        if (viaje == null || viaje.getIdViaje() == null || viaje.getIdViaje().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM VIAJES WHERE ID_VIAJE = ?", Viajes::mapear, viaje.getIdViaje());
    }

    public void agrega(Viaje v) throws PersistenciaException {
        validar(v);
        Bd.insertarConCodigo("VIAJES", "ID_VIAJE", "VIA", 3, v::setIdViaje,
                "INSERT INTO VIAJES (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", id -> valores(id, v));
    }

    public void actualiza(Viaje v) throws PersistenciaException {
        validar(v);
        int n = Bd.actualizar("UPDATE VIAJES SET ID_BUS=?, ID_RUTA=?, FECHA_SALIDA=?, FECHA_LLEGADA=?, ASIENTOS_DISPONIBLES=?, ESTADO=?, ID_EMPRESA=?, "
                        + "HORA_SALIDA=?, HORA_LLEGADA=?, PRECIO=?, ID_CONDUCTOR1=?, ID_CONDUCTOR2=? WHERE ID_VIAJE=?",
                v.getBus().getIdBus(), v.getRuta().getIdRuta(), v.getFechaSalida(), v.getFechaLlegada(), v.getAsientosDisponibles(), v.getEstado(),
                v.getEmpresa().getIdEmpresa(), v.getHoraSalida(), v.getHoraLlegada(), v.getPrecio(), idConductor(v.getConductor1()),
                idConductor(v.getConductor2()), v.getIdViaje());
        if (n == 0) {
            throw new PersistenciaException("El viaje no existe");
        }
    }

    public void elimina(Viaje v) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM VIAJES WHERE ID_VIAJE = ?", v.getIdViaje()) == 0) {
            throw new PersistenciaException("El viaje no existe");
        }
    }

    public List<Viaje> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM VIAJES ORDER BY ID_VIAJE", Viajes::mapear);
    }

    public List<Viaje> listaPorRuta(String idRuta) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM VIAJES WHERE ID_RUTA = ? ORDER BY ID_VIAJE", Viajes::mapear, idRuta);
    }

    public List<Viaje> listaPorEstado(String estado) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM VIAJES WHERE ESTADO = ? ORDER BY ID_VIAJE", Viajes::mapear, estado);
    }
}
