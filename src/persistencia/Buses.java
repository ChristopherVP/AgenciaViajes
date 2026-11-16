package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import excepciones.Validador;
import java.util.List;
import objetosNegocio.Bus;
import objetosNegocio.Empresa;

/** Buses en la tabla BUSES de Oracle. */
public class Buses {
    private static final String COLUMNAS = "ID_BUS, PLACA, NUMERO_CHASIS, NUMERO_MOTOR, MARCA, ANIO_FABRICACION, CAPACIDAD, ESTADO, ID_EMPRESA";

    public Buses(String ignorado) {
    }

    private static Bus mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Bus b = new Bus();
        b.setIdBus(Bd.texto(rs, "ID_BUS"));
        b.setPlaca(Bd.texto(rs, "PLACA"));
        b.setNumeroChasis(Bd.texto(rs, "NUMERO_CHASIS"));
        b.setNumeroMotor(Bd.texto(rs, "NUMERO_MOTOR"));
        b.setMarca(Bd.texto(rs, "MARCA"));
        b.setAnioFabricacion(rs.getInt("ANIO_FABRICACION"));
        b.setCapacidad(rs.getInt("CAPACIDAD"));
        b.setEstado(Bd.texto(rs, "ESTADO"));
        b.setEmpresa(new Empresa(Bd.texto(rs, "ID_EMPRESA")));
        return b;
    }

    private static String idEmpresa(Bus b) {
        return b.getEmpresa() != null ? b.getEmpresa().getIdEmpresa() : "";
    }

    private void validar(Bus bus) throws PersistenciaException {
        if (!Validador.validarNumeroPositivo(bus.getCapacidad())) {
            throw new PersistenciaException(Validador.getMensajeErrorNumeroPositivo("La capacidad"));
        } else if (!Validador.validarNoVacio(bus.getPlaca())) {
            throw new PersistenciaException("La placa no puede estar vacía");
        } else if (!Validador.validarNoVacio(bus.getMarca())) {
            throw new PersistenciaException("La marca no puede estar vacía");
        } else if (!Validador.validarNoVacio(bus.getEstado())) {
            throw new PersistenciaException("El estado no puede estar vacío");
        }
    }

    public Bus obten(Bus bus) throws PersistenciaException {
        if (bus == null || bus.getIdBus() == null || bus.getIdBus().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM BUSES WHERE ID_BUS = ?", Buses::mapear, bus.getIdBus());
    }

    public void agrega(Bus b) throws PersistenciaException {
        validar(b);
        Bd.insertarConCodigo("BUSES", "ID_BUS", "BUS", 3, b::setIdBus,
                "INSERT INTO BUSES (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?)",
                id -> new Object[]{id, b.getPlaca(), b.getNumeroChasis(), b.getNumeroMotor(), b.getMarca(), b.getAnioFabricacion(),
                        b.getCapacidad(), b.getEstado(), idEmpresa(b)});
    }

    public void actualiza(Bus b) throws PersistenciaException {
        validar(b);
        int n = Bd.actualizar("UPDATE BUSES SET PLACA=?, NUMERO_CHASIS=?, NUMERO_MOTOR=?, MARCA=?, ANIO_FABRICACION=?, CAPACIDAD=?, ESTADO=?, ID_EMPRESA=? WHERE ID_BUS=?",
                b.getPlaca(), b.getNumeroChasis(), b.getNumeroMotor(), b.getMarca(), b.getAnioFabricacion(), b.getCapacidad(), b.getEstado(), idEmpresa(b), b.getIdBus());
        if (n == 0) {
            throw new PersistenciaException("El bus no existe");
        }
    }

    public void elimina(Bus b) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM BUSES WHERE ID_BUS = ?", b.getIdBus()) == 0) {
            throw new PersistenciaException("El bus no existe");
        }
    }

    public List<Bus> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM BUSES ORDER BY ID_BUS", Buses::mapear);
    }

    public List<Bus> listaPorEstado(String estado) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM BUSES WHERE ESTADO = ? ORDER BY ID_BUS", Buses::mapear, estado);
    }

    public List<Bus> listaPorEmpresa(String idEmpresa) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM BUSES WHERE ID_EMPRESA = ? ORDER BY ID_BUS", Buses::mapear, idEmpresa);
    }
}
