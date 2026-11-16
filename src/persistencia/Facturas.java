package persistencia;

import DB.Bd;
import excepciones.PersistenciaException;
import excepciones.Validador;
import java.util.List;
import objetosNegocio.Empresa;
import objetosNegocio.Factura;
import objetosNegocio.Pasajero;
import objetosNegocio.Reserva;

/** Facturas de reservas en la tabla FACTURAS de Oracle (los comprobantes del libro de facturación están en COMPROBANTES). */
public class Facturas {
    private static final String COLUMNAS = "ID_FACTURA, ID_PASAJERO, ID_RESERVA, TOTAL, FECHA_EMISION, ESTADO, ID_EMPRESA, NOMBRE_EMPRESA_SNAPSHOT, "
            + "RUC_EMPRESA_SNAPSHOT, DIRECCION_EMPRESA_SNAPSHOT, NOMBRE_VENDEDOR, DATOS_PASAJERO_SNAPSHOT";

    public Facturas(String ignorado) {
    }

    private static Factura mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Factura f = new Factura();
        f.setIdFactura(Bd.texto(rs, "ID_FACTURA"));
        f.setPasajero(new Pasajero(Bd.texto(rs, "ID_PASAJERO")));
        f.setReserva(new Reserva(Bd.texto(rs, "ID_RESERVA")));
        f.setTotal(rs.getDouble("TOTAL"));
        f.setFechaEmision(Bd.fecha(rs, "FECHA_EMISION"));
        f.setEstado(Bd.texto(rs, "ESTADO"));
        f.setEmpresa(new Empresa(Bd.texto(rs, "ID_EMPRESA")));
        f.setNombreEmpresaSnapshot(Bd.texto(rs, "NOMBRE_EMPRESA_SNAPSHOT"));
        f.setRucEmpresaSnapshot(Bd.texto(rs, "RUC_EMPRESA_SNAPSHOT"));
        f.setDireccionEmpresaSnapshot(Bd.texto(rs, "DIRECCION_EMPRESA_SNAPSHOT"));
        f.setNombreVendedor(Bd.texto(rs, "NOMBRE_VENDEDOR"));
        f.setDatosPasajeroSnapshot(Bd.texto(rs, "DATOS_PASAJERO_SNAPSHOT"));
        return f;
    }

    private void validar(Factura f) throws PersistenciaException {
        if (!Validador.validarNumeroPositivo(f.getTotal())) {
            throw new PersistenciaException(Validador.getMensajeErrorNumeroPositivo("El total"));
        } else if (!Validador.validarNoVacio(f.getEstado())) {
            throw new PersistenciaException("El estado no puede estar vacío");
        } else if (f.getPasajero() == null || !Validador.validarNoVacio(f.getPasajero().getIdPasajero())) {
            throw new PersistenciaException("Debe asignar un pasajero a la factura");
        } else if (f.getReserva() == null || !Validador.validarNoVacio(f.getReserva().getIdReserva())) {
            throw new PersistenciaException("Debe asignar una reserva a la factura");
        } else if (f.getEmpresa() == null || !Validador.validarNoVacio(f.getEmpresa().getIdEmpresa())) {
            throw new PersistenciaException("Debe asignar una empresa a la factura");
        }
    }

    private static Object[] valores(String id, Factura f) {
        return new Object[]{id, f.getPasajero().getIdPasajero(), f.getReserva().getIdReserva(), f.getTotal(), f.getFechaEmision(), f.getEstado(),
                f.getEmpresa().getIdEmpresa(), f.getNombreEmpresaSnapshot(), f.getRucEmpresaSnapshot(), f.getDireccionEmpresaSnapshot(),
                f.getNombreVendedor(), f.getDatosPasajeroSnapshot()};
    }

    public Factura obten(Factura factura) throws PersistenciaException {
        if (factura == null || factura.getIdFactura() == null || factura.getIdFactura().isEmpty()) {
            return null;
        }
        return Bd.uno("SELECT " + COLUMNAS + " FROM FACTURAS WHERE ID_FACTURA = ?", Facturas::mapear, factura.getIdFactura());
    }

    public void agrega(Factura f) throws PersistenciaException {
        validar(f);
        Bd.insertarConCodigo("FACTURAS", "ID_FACTURA", "FAC", 3, f::setIdFactura,
                "INSERT INTO FACTURAS (" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", id -> valores(id, f));
    }

    public void actualiza(Factura f) throws PersistenciaException {
        validar(f);
        int n = Bd.actualizar("UPDATE FACTURAS SET ID_PASAJERO=?, ID_RESERVA=?, TOTAL=?, FECHA_EMISION=?, ESTADO=?, ID_EMPRESA=?, NOMBRE_EMPRESA_SNAPSHOT=?, "
                        + "RUC_EMPRESA_SNAPSHOT=?, DIRECCION_EMPRESA_SNAPSHOT=?, NOMBRE_VENDEDOR=?, DATOS_PASAJERO_SNAPSHOT=? WHERE ID_FACTURA=?",
                f.getPasajero().getIdPasajero(), f.getReserva().getIdReserva(), f.getTotal(), f.getFechaEmision(), f.getEstado(), f.getEmpresa().getIdEmpresa(),
                f.getNombreEmpresaSnapshot(), f.getRucEmpresaSnapshot(), f.getDireccionEmpresaSnapshot(), f.getNombreVendedor(), f.getDatosPasajeroSnapshot(),
                f.getIdFactura());
        if (n == 0) {
            throw new PersistenciaException("La factura no existe");
        }
    }

    public void elimina(Factura f) throws PersistenciaException {
        if (Bd.actualizar("DELETE FROM FACTURAS WHERE ID_FACTURA = ?", f.getIdFactura()) == 0) {
            throw new PersistenciaException("La factura no existe");
        }
    }

    public List<Factura> lista() throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM FACTURAS ORDER BY ID_FACTURA", Facturas::mapear);
    }

    public List<Factura> listaPorPasajero(String idPasajero) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM FACTURAS WHERE ID_PASAJERO = ? ORDER BY ID_FACTURA", Facturas::mapear, idPasajero);
    }

    public List<Factura> listaPorEstado(String estado) throws PersistenciaException {
        return Bd.consultar("SELECT " + COLUMNAS + " FROM FACTURAS WHERE ESTADO = ? ORDER BY ID_FACTURA", Facturas::mapear, estado);
    }
}
