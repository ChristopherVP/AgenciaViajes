package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import excepciones.Validador;
import java.util.List;
import objetosNegocio.Empresa;
import objetosNegocio.Pasajero;
import objetosNegocio.Reserva;
import objetosNegocio.Viaje;

/** Reservas de pasajes en la tabla RESERVAS de Oracle. */
public class Reservas {
    private static final String COLUMNAS = "ID_RESERVA, ID_PASAJERO, ID_VIAJE, CANTIDAD_ASIENTOS, FECHA_RESERVA, ESTADO, ID_EMPRESA, PRECIO_TOTAL, METODO_PAGO, NINOS_BRAZOS";

    public Reservas(String ignorado) {
    }

    private static Reserva mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Reserva r = new Reserva();
        r.setIdReserva(Bd.texto(rs, "ID_RESERVA"));
        r.setPasajero(new Pasajero(Bd.texto(rs, "ID_PASAJERO")));
        r.setViaje(new Viaje(Bd.texto(rs, "ID_VIAJE")));
        r.setCantidadAsientos(rs.getInt("CANTIDAD_ASIENTOS"));
        r.setFechaReserva(Bd.fecha(rs, "FECHA_RESERVA"));
        r.setEstado(Bd.texto(rs, "ESTADO"));
        r.setEmpresa(new Empresa(Bd.texto(rs, "ID_EMPRESA")));
        r.setPrecioTotal(rs.getDouble("PRECIO_TOTAL"));
        r.setMetodoPago(Bd.texto(rs, "METODO_PAGO"));
        r.setNinosBrazos(rs.getInt("NINOS_BRAZOS"));
        return r;
    }

    private void validar(Reserva r) throws PersistenciaException {
        if (!Validador.validarNumeroPositivo(r.getCantidadAsientos())) {
            throw new PersistenciaException(Validador.getMensajeErrorNumeroPositivo("La cantidad de asientos"));
        } else if (!Validador.validarNoVacio(r.getEstado())) {
            throw new PersistenciaException("El estado no puede estar vacío");
        } else if (r.getPasajero() == null || !Validador.validarNoVacio(r.getPasajero().getIdPasajero())) {
            throw new PersistenciaException("Debe asignar un pasajero a la reserva");
        } else if (r.getViaje() == null || !Validador.validarNoVacio(r.getViaje().getIdViaje())) {
            throw new PersistenciaException("Debe asignar un viaje a la reserva");
        } else if (r.getEmpresa() == null || !Validador.validarNoVacio(r.getEmpresa().getIdEmpresa())) {
            throw new PersistenciaException("Debe asignar una empresa a la reserva");
        }
    }

    private static String metodo(Reserva r) {
        return r.getMetodoPago() != null && !r.getMetodoPago().isEmpty() ? r.getMetodoPago() : "EFECTIVO";
    }

    public Reserva obten(Reserva reserva) throws PersistenciaException {
        if (reserva == null || reserva.getIdReserva() == null || reserva.getIdReserva().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM RESERVAS WHERE ID_RESERVA = ?", Reservas::mapear, reserva.getIdReserva());
    }

    public void agrega(Reserva r) throws PersistenciaException {
        validar(r);
        Bd.insertarConCodigo("RESERVAS", "ID_RESERVA", "RES", 3, r::setIdReserva,
                "INSERT INTO RESERVAS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?)",
                id -> new Object[]{id, r.getPasajero().getIdPasajero(), r.getViaje().getIdViaje(), r.getCantidadAsientos(), r.getFechaReserva(),
                        r.getEstado(), r.getEmpresa().getIdEmpresa(), r.getPrecioTotal(), metodo(r), r.getNinosBrazos()});
    }

    public void actualiza(Reserva r) throws PersistenciaException {
        validar(r);
        int n = Bd.actualizar("UPDATE RESERVAS SET ID_PASAJERO=?, ID_VIAJE=?, CANTIDAD_ASIENTOS=?, FECHA_RESERVA=?, ESTADO=?, ID_EMPRESA=?, PRECIO_TOTAL=?, METODO_PAGO=?, NINOS_BRAZOS=? WHERE ID_RESERVA=?",
                r.getPasajero().getIdPasajero(), r.getViaje().getIdViaje(), r.getCantidadAsientos(), r.getFechaReserva(), r.getEstado(),
                r.getEmpresa().getIdEmpresa(), r.getPrecioTotal(), metodo(r), r.getNinosBrazos(), r.getIdReserva());
        if (n == 0) {
            throw new PersistenciaException("La reserva no existe");
        }
    }

    public void elimina(Reserva r) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM RESERVAS WHERE ID_RESERVA = ?", r.getIdReserva()) == 0) {
            throw new PersistenciaException("La reserva no existe");
        }
    }

    public List<Reserva> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM RESERVAS ORDER BY ID_RESERVA", Reservas::mapear);
    }

    public List<Reserva> listaPorPasajero(String idPasajero) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM RESERVAS WHERE ID_PASAJERO = ? ORDER BY ID_RESERVA", Reservas::mapear, idPasajero);
    }

    public List<Reserva> listaPorViaje(String idViaje) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM RESERVAS WHERE ID_VIAJE = ? ORDER BY ID_RESERVA", Reservas::mapear, idViaje);
    }
}
