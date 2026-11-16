package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import excepciones.Validador;
import java.util.List;
import objetosNegocio.Empresa;
import objetosNegocio.Pasajero;

/** Pasajeros en la tabla PASAJEROS de Oracle. */
public class Pasajeros {
    private static final String COLUMNAS = "ID_PASAJERO, NOMBRE, CEDULA, TELEFONO, EMAIL, DIRECCION, ID_EMPRESA, FECHA_NACIMIENTO, DISCAPACIDAD";

    public Pasajeros(String ignorado) {
    }

    private static Pasajero mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Pasajero p = new Pasajero();
        p.setIdPasajero(Bd.texto(rs, "ID_PASAJERO"));
        p.setNombre(Bd.texto(rs, "NOMBRE"));
        p.setCedula(Bd.texto(rs, "CEDULA"));
        p.setTelefono(Bd.texto(rs, "TELEFONO"));
        p.setEmail(Bd.texto(rs, "EMAIL"));
        p.setDireccion(Bd.texto(rs, "DIRECCION"));
        p.setEmpresa(new Empresa(Bd.texto(rs, "ID_EMPRESA")));
        p.setFechaNacimiento(Bd.fecha(rs, "FECHA_NACIMIENTO"));
        p.setDiscapacidad(Bd.logico(rs, "DISCAPACIDAD"));
        return p;
    }

    private static String idEmpresa(Pasajero p) {
        return p.getEmpresa() != null ? p.getEmpresa().getIdEmpresa() : "";
    }

    private static void validar(Pasajero p) throws PersistenciaException {
        if (!Validador.validarNoVacio(p.getNombre())) {
            throw new PersistenciaException("El nombre del pasajero es obligatorio");
        }
    }

    public void agrega(Pasajero p) throws PersistenciaException {
        validar(p);
        Bd.insertarConCodigo("PASAJEROS", "ID_PASAJERO", "PAS", 3, p::setIdPasajero,
                "INSERT INTO PASAJEROS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?)",
                id -> new Object[]{id, p.getNombre(), p.getCedula(), p.getTelefono(), p.getEmail(), p.getDireccion(), idEmpresa(p),
                        p.getFechaNacimiento(), p.isDiscapacidad()});
    }

    public Pasajero obten(Pasajero busqueda) throws PersistenciaException {
        if (busqueda == null || busqueda.getIdPasajero() == null || busqueda.getIdPasajero().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM PASAJEROS WHERE ID_PASAJERO = ?", Pasajeros::mapear, busqueda.getIdPasajero());
    }

    public void actualiza(Pasajero p) throws PersistenciaException {
        validar(p);
        int n = Bd.actualizar("UPDATE PASAJEROS SET NOMBRE=?, CEDULA=?, TELEFONO=?, EMAIL=?, DIRECCION=?, ID_EMPRESA=?, FECHA_NACIMIENTO=?, DISCAPACIDAD=? WHERE ID_PASAJERO=?",
                p.getNombre(), p.getCedula(), p.getTelefono(), p.getEmail(), p.getDireccion(), idEmpresa(p), p.getFechaNacimiento(), p.isDiscapacidad(), p.getIdPasajero());
        if (n == 0) {
            throw new PersistenciaException("El pasajero no existe");
        }
    }

    public void elimina(Pasajero p) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM PASAJEROS WHERE ID_PASAJERO = ?", p.getIdPasajero()) == 0) {
            throw new PersistenciaException("El pasajero no existe");
        }
    }

    public List<Pasajero> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM PASAJEROS ORDER BY ID_PASAJERO", Pasajeros::mapear);
    }

    public List<Pasajero> listaPorEmpresa(String idEmpresa) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM PASAJEROS WHERE ID_EMPRESA = ? ORDER BY ID_PASAJERO", Pasajeros::mapear, idEmpresa);
    }
}
