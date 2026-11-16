package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import excepciones.Validador;
import java.util.List;
import objetosNegocio.Empresa;
import objetosNegocio.Ruta;

/** Rutas en la tabla RUTAS de Oracle. */
public class Rutas {
    private static final String COLUMNAS = "ID_RUTA, ORIGEN, DESTINO, DISTANCIA, PRECIO, ID_EMPRESA, TIEMPO_ESTIMADO, PARADAS, TIEMPO_DETENCION, ADVERTENCIAS, ESTADO";

    public Rutas(String ignorado) {
    }

    private static Ruta mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Ruta r = new Ruta();
        r.setIdRuta(Bd.texto(rs, "ID_RUTA"));
        r.setOrigen(Bd.texto(rs, "ORIGEN"));
        r.setDestino(Bd.texto(rs, "DESTINO"));
        r.setDistancia(rs.getDouble("DISTANCIA"));
        r.setPrecio(rs.getDouble("PRECIO"));
        r.setEmpresa(new Empresa(Bd.texto(rs, "ID_EMPRESA")));
        r.setTiempoEstimado(rs.getDouble("TIEMPO_ESTIMADO"));
        r.setParadas(Bd.texto(rs, "PARADAS"));
        r.setTiempoDetencion(rs.getInt("TIEMPO_DETENCION"));
        r.setAdvertencias(Bd.texto(rs, "ADVERTENCIAS"));
        r.setEstado(Bd.texto(rs, "ESTADO"));
        return r;
    }

    private static String idEmpresa(Ruta r) {
        return r.getEmpresa() != null ? r.getEmpresa().getIdEmpresa() : "";
    }

    private static String estado(Ruta r) {
        return r.getEstado() != null && !r.getEstado().isEmpty() ? r.getEstado() : "ACTIVA";
    }

    private void validar(Ruta ruta) throws PersistenciaException {
        if (!Validador.validarNoVacio(ruta.getOrigen())) {
            throw new PersistenciaException("El origen no puede estar vacío");
        } else if (!Validador.validarNoVacio(ruta.getDestino())) {
            throw new PersistenciaException("El destino no puede estar vacío");
        } else if (ruta.getOrigen().trim().equalsIgnoreCase(ruta.getDestino().trim())) {
            throw new PersistenciaException("El origen y destino deben ser diferentes");
        } else if (!Validador.validarNumeroPositivo(ruta.getDistancia())) {
            throw new PersistenciaException(Validador.getMensajeErrorNumeroPositivo("La distancia"));
        } else if (!Validador.validarNumeroPositivo(ruta.getPrecio())) {
            throw new PersistenciaException(Validador.getMensajeErrorNumeroPositivo("El precio"));
        } else if (ruta.getEmpresa() == null || !Validador.validarNoVacio(ruta.getEmpresa().getIdEmpresa())) {
            throw new PersistenciaException("Debe asignar una empresa a la ruta");
        }
    }

    public Ruta obten(Ruta ruta) throws PersistenciaException {
        if (ruta == null || ruta.getIdRuta() == null || ruta.getIdRuta().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM RUTAS WHERE ID_RUTA = ?", Rutas::mapear, ruta.getIdRuta());
    }

    public void agrega(Ruta r) throws PersistenciaException {
        validar(r);
        Bd.insertarConCodigo("RUTAS", "ID_RUTA", "RUTA", 3, r::setIdRuta,
                "INSERT INTO RUTAS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id -> new Object[]{id, r.getOrigen(), r.getDestino(), r.getDistancia(), r.getPrecio(), idEmpresa(r), r.getTiempoEstimado(),
                        r.getParadas(), r.getTiempoDetencion(), r.getAdvertencias(), estado(r)});
    }

    public void actualiza(Ruta r) throws PersistenciaException {
        validar(r);
        int n = Bd.actualizar("UPDATE RUTAS SET ORIGEN=?, DESTINO=?, DISTANCIA=?, PRECIO=?, ID_EMPRESA=?, TIEMPO_ESTIMADO=?, PARADAS=?, TIEMPO_DETENCION=?, ADVERTENCIAS=?, ESTADO=? WHERE ID_RUTA=?",
                r.getOrigen(), r.getDestino(), r.getDistancia(), r.getPrecio(), idEmpresa(r), r.getTiempoEstimado(), r.getParadas(),
                r.getTiempoDetencion(), r.getAdvertencias(), estado(r), r.getIdRuta());
        if (n == 0) {
            throw new PersistenciaException("La ruta no existe");
        }
    }

    public void elimina(Ruta r) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM RUTAS WHERE ID_RUTA = ?", r.getIdRuta()) == 0) {
            throw new PersistenciaException("La ruta no existe");
        }
    }

    public List<Ruta> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM RUTAS ORDER BY ID_RUTA", Rutas::mapear);
    }

    public List<Ruta> listaPorOrigen(String origen) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM RUTAS WHERE ORIGEN = ? ORDER BY ID_RUTA", Rutas::mapear, origen);
    }

    public List<Ruta> listaPorDestino(String destino) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM RUTAS WHERE DESTINO = ? ORDER BY ID_RUTA", Rutas::mapear, destino);
    }
}
