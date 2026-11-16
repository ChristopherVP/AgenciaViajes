package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import excepciones.Validador;
import java.util.List;
import objetosNegocio.Conductor;
import objetosNegocio.Empresa;

/** Conductores en la tabla CONDUCTORES de Oracle. */
public class Conductores {
    private static final String COLUMNAS = "ID_CONDUCTOR, NOMBRE, CEDULA, TELEFONO, LICENCIA, ESTADO, ID_EMPRESA";

    public Conductores(String ignorado) {
    }

    private static Conductor mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Conductor c = new Conductor();
        c.setIdConductor(Bd.texto(rs, "ID_CONDUCTOR"));
        c.setNombre(Bd.texto(rs, "NOMBRE"));
        c.setCedula(Bd.texto(rs, "CEDULA"));
        c.setTelefono(Bd.texto(rs, "TELEFONO"));
        c.setLicencia(Bd.texto(rs, "LICENCIA"));
        c.setEstado(Bd.texto(rs, "ESTADO"));
        c.setEmpresa(new Empresa(Bd.texto(rs, "ID_EMPRESA")));
        return c;
    }

    private static String idEmpresa(Conductor c) {
        return c.getEmpresa() != null ? c.getEmpresa().getIdEmpresa() : "";
    }

    private void validar(Conductor c) throws PersistenciaException {
        if (!Validador.validarNoVacio(c.getNombre())) {
            throw new PersistenciaException("El nombre es obligatorio");
        } else if (!Validador.validarCedulaEcuatoriana(c.getCedula())) {
            throw new PersistenciaException(Validador.getMensajeErrorCedula());
        } else if (!Validador.validarNoVacio(c.getLicencia())) {
            throw new PersistenciaException("La licencia es obligatoria");
        } else if (c.getEmpresa() == null || !Validador.validarNoVacio(c.getEmpresa().getIdEmpresa())) {
            throw new PersistenciaException("El conductor debe pertenecer a una empresa");
        }
    }

    public void agrega(Conductor c) throws PersistenciaException {
        if (c.getEstado() == null) {
            c.setEstado("ACTIVO");
        }
        validar(c);
        Bd.insertarConCodigo("CONDUCTORES", "ID_CONDUCTOR", "CDN", 3, c::setIdConductor,
                "INSERT INTO CONDUCTORES (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?)",
                id -> new Object[]{id, c.getNombre(), c.getCedula(), c.getTelefono(), c.getLicencia(), c.getEstado(), idEmpresa(c)});
    }

    public Conductor obten(Conductor busqueda) throws PersistenciaException {
        if (busqueda == null || busqueda.getIdConductor() == null || busqueda.getIdConductor().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM CONDUCTORES WHERE ID_CONDUCTOR = ?", Conductores::mapear, busqueda.getIdConductor());
    }

    public void actualiza(Conductor c) throws PersistenciaException {
        validar(c);
        int n = Bd.actualizar("UPDATE CONDUCTORES SET NOMBRE=?, CEDULA=?, TELEFONO=?, LICENCIA=?, ESTADO=?, ID_EMPRESA=? WHERE ID_CONDUCTOR=?",
                c.getNombre(), c.getCedula(), c.getTelefono(), c.getLicencia(), c.getEstado(), idEmpresa(c), c.getIdConductor());
        if (n == 0) {
            throw new PersistenciaException("El conductor no existe");
        }
    }

    public void elimina(Conductor c) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM CONDUCTORES WHERE ID_CONDUCTOR = ?", c.getIdConductor()) == 0) {
            throw new PersistenciaException("El conductor no existe");
        }
    }

    public List<Conductor> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM CONDUCTORES ORDER BY ID_CONDUCTOR", Conductores::mapear);
    }

    public List<Conductor> listaPorEmpresa(String idEmpresa) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM CONDUCTORES WHERE ID_EMPRESA = ? ORDER BY ID_CONDUCTOR", Conductores::mapear, idEmpresa);
    }
}
