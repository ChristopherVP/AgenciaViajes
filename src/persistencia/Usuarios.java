package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import java.util.List;
import objetosNegocio.Cargo;
import objetosNegocio.Empresa;
import objetosNegocio.Rol;
import objetosNegocio.Usuario;
import objetosServicio.Seguridad;

/** Usuarios del sistema en la tabla USUARIOS de Oracle. Las contraseñas se guardan siempre como hash. */
public class Usuarios {
    private static final String COLUMNAS = "ID_USUARIO, LOGIN, NOMBRE, CEDULA, CONTRASENA, EMAIL, TELEFONO, TIPO, FECHA_REGISTRO, ID_EMPRESA, ESTADO, "
            + "PREGUNTA_SEGURIDAD, RESPUESTA_SEGURIDAD, ID_PERFIL, CARGO, INTENTOS_RECUPERACION, BLOQUEO_HASTA, CLAVE_PENDIENTE";

    public Usuarios(String ignorado) {
    }

    private static Usuario mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Usuario u = new Usuario();
        u.setIdUsuario(Bd.texto(rs, "ID_USUARIO"));
        u.setLogin(Bd.texto(rs, "LOGIN"));
        u.setNombre(Bd.texto(rs, "NOMBRE"));
        u.setCedula(Bd.texto(rs, "CEDULA"));
        u.setContrasena(Bd.texto(rs, "CONTRASENA"));
        u.setEmail(Bd.texto(rs, "EMAIL"));
        u.setTelefono(Bd.texto(rs, "TELEFONO"));
        u.setTipo(Bd.texto(rs, "TIPO"));
        u.setFechaRegistro(Bd.fecha(rs, "FECHA_REGISTRO"));
        String idEmpresa = Bd.texto(rs, "ID_EMPRESA");
        u.setEmpresa(idEmpresa.isEmpty() ? null : new Empresa(idEmpresa));
        u.setEstado(Bd.texto(rs, "ESTADO"));
        u.setPreguntaSeguridad(Bd.texto(rs, "PREGUNTA_SEGURIDAD"));
        u.setRespuestaSeguridad(Bd.texto(rs, "RESPUESTA_SEGURIDAD"));
        u.setIdPerfil(Bd.texto(rs, "ID_PERFIL"));
        u.setCargo(Bd.texto(rs, "CARGO"));
        u.setIntentosRecuperacion(rs.getInt("INTENTOS_RECUPERACION"));
        u.setBloqueoHasta(rs.getLong("BLOQUEO_HASTA"));
        u.setClavePendiente(rs.getInt("CLAVE_PENDIENTE") == 1);
        return u;
    }

    private static Object[] valores(String id, Usuario u) {
        return new Object[]{id, u.getLogin(), u.getNombre(), u.getCedula(), u.getContrasena(), u.getEmail(), u.getTelefono(), u.getTipo(),
                u.getFechaRegistro(), u.tieneEmpresa() ? u.getEmpresa().getIdEmpresa() : "", u.getEstado(), u.getPreguntaSeguridad(),
                u.getRespuestaSeguridad(), u.getIdPerfil(), u.getCargo(), u.getIntentosRecuperacion(), u.getBloqueoHasta(), u.isClavePendiente()};
    }

    private void validar(Usuario u) throws PersistenciaException {
        if (u.getLogin() == null || u.getLogin().trim().isEmpty()) {
            throw new PersistenciaException("El nombre de usuario es obligatorio");
        }
        if (u.getNombre() == null || u.getNombre().trim().isEmpty()) {
            throw new PersistenciaException("El nombre no puede estar vacío");
        }
        if (u.getCedula() == null || !u.getCedula().trim().matches("\\d{10}")) {
            throw new PersistenciaException("La cédula debe tener 10 dígitos");
        }
        if (!Seguridad.esHash(u.getContrasena())) {
            throw new PersistenciaException("La contraseña debe almacenarse protegida (hash)");
        }
        Rol rol = u.getRol();
        if (rol == null) {
            throw new PersistenciaException("Rol de usuario inválido: " + u.getTipo());
        }
        if (u.getCargo() != null && u.getCargo().length() > Cargo.LONGITUD_MAXIMA) {
            throw new PersistenciaException("El cargo no puede superar " + Cargo.LONGITUD_MAXIMA + " caracteres");
        }
        if (rol == Rol.OPERADOR && !u.tieneEmpresa()) {
            throw new PersistenciaException("Los operadores deben pertenecer a una empresa");
        }
        if ((rol == Rol.CONDUCTOR || rol == Rol.PASAJERO) && (u.getIdPerfil() == null || u.getIdPerfil().trim().isEmpty())) {
            throw new PersistenciaException("Los conductores y pasajeros deben tener una ficha asociada");
        }
        if (u.getEstado() == null || u.getEstado().trim().isEmpty()) {
            throw new PersistenciaException("El estado del usuario es obligatorio");
        }
    }

    public synchronized void agrega(Usuario usuario) throws PersistenciaException {
        validar(usuario);
        Bd.insertarConCodigo("USUARIOS", "ID_USUARIO", "USU", 3, usuario::setIdUsuario,
                "INSERT INTO USUARIOS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", id -> valores(id, usuario));
    }

    /** Agrega conservando el id recibido. */
    public synchronized void importa(Usuario usuario) throws PersistenciaException {
        validar(usuario);
        if (usuario.getIdUsuario() == null || usuario.getIdUsuario().trim().isEmpty()) {
            throw new PersistenciaException("El usuario importado necesita un id");
        }
        Bd.actualizar("INSERT INTO USUARIOS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", valores(usuario.getIdUsuario(), usuario));
    }

    public synchronized Usuario obten(Usuario usuario) throws PersistenciaException {
        if (usuario == null || usuario.getIdUsuario() == null) {
            return null;
        }
        return obtenPorIdUsuario(usuario.getIdUsuario());
    }

    public synchronized Usuario obtenPorIdUsuario(String idUsuario) throws PersistenciaException {
        return Bd.uno("SELECT " + COLUMNAS + " FROM USUARIOS WHERE ID_USUARIO = ?", Usuarios::mapear, idUsuario);
    }

    public synchronized Usuario buscaPorLogin(String login) throws PersistenciaException {
        if (login == null) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM USUARIOS WHERE UPPER(LOGIN) = UPPER(?)", Usuarios::mapear, login.trim());
    }

    public synchronized Usuario buscaPorCedula(String cedula) throws PersistenciaException {
        if (cedula == null) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM USUARIOS WHERE CEDULA = ? ORDER BY ID_USUARIO", Usuarios::mapear, cedula.trim());
    }

    public synchronized void actualiza(Usuario u) throws PersistenciaException {
        validar(u);
        int n = Bd.actualizar("UPDATE USUARIOS SET LOGIN=?, NOMBRE=?, CEDULA=?, CONTRASENA=?, EMAIL=?, TELEFONO=?, TIPO=?, FECHA_REGISTRO=?, ID_EMPRESA=?, ESTADO=?, "
                        + "PREGUNTA_SEGURIDAD=?, RESPUESTA_SEGURIDAD=?, ID_PERFIL=?, CARGO=?, INTENTOS_RECUPERACION=?, BLOQUEO_HASTA=?, CLAVE_PENDIENTE=? WHERE ID_USUARIO=?",
                u.getLogin(), u.getNombre(), u.getCedula(), u.getContrasena(), u.getEmail(), u.getTelefono(), u.getTipo(), u.getFechaRegistro(),
                u.tieneEmpresa() ? u.getEmpresa().getIdEmpresa() : "", u.getEstado(), u.getPreguntaSeguridad(), u.getRespuestaSeguridad(),
                u.getIdPerfil(), u.getCargo(), u.getIntentosRecuperacion(), u.getBloqueoHasta(), u.isClavePendiente(), u.getIdUsuario());
        if (n == 0) {
            throw new PersistenciaException("Usuario no encontrado: " + u.getIdUsuario());
        }
    }

    public synchronized void elimina(Usuario usuario) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM USUARIOS WHERE ID_USUARIO = ?", usuario.getIdUsuario()) == 0) {
            throw new PersistenciaException("Usuario no encontrado: " + usuario.getIdUsuario());
        }
    }

    public synchronized List<Usuario> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM USUARIOS ORDER BY ID_USUARIO", Usuarios::mapear);
    }

    public List<Usuario> listaPorTipo(String tipo) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM USUARIOS WHERE UPPER(TIPO) = UPPER(?) ORDER BY ID_USUARIO", Usuarios::mapear, tipo);
    }
}
