package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import excepciones.Validador;
import java.util.List;
import objetosNegocio.Empresa;

/** Empresas de transporte en la tabla EMPRESAS de Oracle. El parámetro del constructor se conserva solo por compatibilidad. */
public class Empresas {
    private static final String COLUMNAS = "ID_EMPRESA, NOMBRE, RUC, DIRECCION, TELEFONO, EMAIL";

    public Empresas(String ignorado) {
    }

    private static Empresa mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Empresa e = new Empresa();
        e.setIdEmpresa(Bd.texto(rs, "ID_EMPRESA"));
        e.setNombre(Bd.texto(rs, "NOMBRE"));
        e.setRuc(Bd.texto(rs, "RUC"));
        e.setDireccion(Bd.texto(rs, "DIRECCION"));
        e.setTelefono(Bd.texto(rs, "TELEFONO"));
        e.setEmail(Bd.texto(rs, "EMAIL"));
        return e;
    }

    private void validar(Empresa empresa) throws PersistenciaException {
        if (!Validador.validarNoVacio(empresa.getNombre())) {
            throw new PersistenciaException("El nombre de la empresa no puede estar vacío");
        } else if (!Validador.validarRuc(empresa.getRuc())) {
            throw new PersistenciaException(Validador.getMensajeErrorRuc());
        } else if (!Validador.validarEmail(empresa.getEmail())) {
            throw new PersistenciaException(Validador.getMensajeErrorEmail());
        } else if (empresa.getTelefono() != null && !empresa.getTelefono().trim().isEmpty() && !Validador.validarTelefono(empresa.getTelefono())) {
            throw new PersistenciaException("Teléfono inválido. Debe tener entre 9 y 10 dígitos");
        } else if (!Validador.validarNoVacio(empresa.getDireccion())) {
            throw new PersistenciaException("La dirección no puede estar vacía");
        }
    }

    public Empresa obten(Empresa empresa) throws PersistenciaException {
        if (empresa == null || empresa.getIdEmpresa() == null || empresa.getIdEmpresa().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM EMPRESAS WHERE ID_EMPRESA = ?", Empresas::mapear, empresa.getIdEmpresa());
    }

    public void agrega(Empresa e) throws PersistenciaException {
        validar(e);
        Bd.insertarConCodigo("EMPRESAS", "ID_EMPRESA", "EMP", 3, e::setIdEmpresa,
                "INSERT INTO EMPRESAS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?)",
                id -> new Object[]{id, e.getNombre(), e.getRuc(), e.getDireccion(), e.getTelefono(), e.getEmail()});
    }

    public void actualiza(Empresa e) throws PersistenciaException {
        validar(e);
        int n = Bd.actualizar("UPDATE EMPRESAS SET NOMBRE=?, RUC=?, DIRECCION=?, TELEFONO=?, EMAIL=? WHERE ID_EMPRESA=?",
                e.getNombre(), e.getRuc(), e.getDireccion(), e.getTelefono(), e.getEmail(), e.getIdEmpresa());
        if (n == 0) {
            throw new PersistenciaException("La empresa no existe");
        }
    }

    public void elimina(Empresa e) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM EMPRESAS WHERE ID_EMPRESA = ?", e.getIdEmpresa()) == 0) {
            throw new PersistenciaException("La empresa no existe");
        }
    }

    public List<Empresa> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM EMPRESAS ORDER BY ID_EMPRESA", Empresas::mapear);
    }
}
