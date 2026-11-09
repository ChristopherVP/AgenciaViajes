package objetosServicio;

import java.util.EnumSet;
import java.util.Set;
import objetosNegocio.Cargo;
import objetosNegocio.Rol;
import objetosNegocio.Usuario;

/**
 * Qué puede ver y qué puede modificar cada cargo de administración. Cada rol tiene su responsabilidad:
 * finanzas maneja recargas, facturas y pagos; operaciones aprueba conductores y viajes; rutas maneja rutas, etc.
 */
public final class Permisos {
    public enum Modulo {
        EMPRESAS, USUARIOS, CONDUCTORES, PASAJEROS, BUSES, RUTAS, VIAJES, RESERVAS, FACTURAS, NOVEDADES, RECARGAS, PAGOS, REPORTES
    }

    private Permisos() {
    }

    private static Set<Modulo> todos() {
        return EnumSet.allOf(Modulo.class);
    }

    /** Módulos que el cargo puede VER. */
    public static Set<Modulo> ve(Usuario u) {
        if (u == null || u.getRol() == Rol.PASAJERO || u.getRol() == Rol.CONDUCTOR) {
            return EnumSet.noneOf(Modulo.class);
        }
        if (u.getRol() == Rol.OPERADOR) {
            return EnumSet.of(Modulo.EMPRESAS, Modulo.CONDUCTORES, Modulo.BUSES, Modulo.RUTAS, Modulo.VIAJES, Modulo.RESERVAS, Modulo.NOVEDADES);
        }
        Cargo cargo = Cargo.desdeEtiqueta(u.getCargo());
        if (cargo == null || cargo == Cargo.ADMINISTRADOR_PRINCIPAL || cargo == Cargo.GERENTE_GENERAL) {
            return todos();
        }
        switch (cargo) {
            case SUPERVISOR_OPERACIONES:
                return EnumSet.of(Modulo.EMPRESAS, Modulo.USUARIOS, Modulo.CONDUCTORES, Modulo.BUSES, Modulo.RUTAS, Modulo.VIAJES, Modulo.RESERVAS, Modulo.NOVEDADES, Modulo.REPORTES);
            case COORDINADOR_RUTAS:
                return EnumSet.of(Modulo.EMPRESAS, Modulo.BUSES, Modulo.RUTAS, Modulo.VIAJES, Modulo.RESERVAS, Modulo.REPORTES);
            case JEFE_FINANZAS:
                return EnumSet.of(Modulo.EMPRESAS, Modulo.RESERVAS, Modulo.FACTURAS, Modulo.RECARGAS, Modulo.PAGOS, Modulo.REPORTES);
            case ATENCION_CLIENTE:
                return EnumSet.of(Modulo.PASAJEROS, Modulo.RESERVAS, Modulo.FACTURAS, Modulo.NOVEDADES, Modulo.RECARGAS);
            default:
                return EnumSet.of(Modulo.NOVEDADES);
        }
    }

    /** Módulos donde el cargo puede AGREGAR, EDITAR o APROBAR (el resto es solo consulta). */
    public static Set<Modulo> edita(Usuario u) {
        if (u == null || u.getRol() == Rol.PASAJERO || u.getRol() == Rol.CONDUCTOR) {
            return EnumSet.noneOf(Modulo.class);
        }
        if (u.getRol() == Rol.OPERADOR) {
            return EnumSet.of(Modulo.BUSES, Modulo.RUTAS, Modulo.VIAJES, Modulo.CONDUCTORES, Modulo.NOVEDADES);
        }
        Cargo cargo = Cargo.desdeEtiqueta(u.getCargo());
        if (cargo == null || cargo == Cargo.ADMINISTRADOR_PRINCIPAL) {
            return todos();
        }
        switch (cargo) {
            case GERENTE_GENERAL:
                return EnumSet.of(Modulo.EMPRESAS, Modulo.USUARIOS, Modulo.CONDUCTORES, Modulo.BUSES, Modulo.RUTAS, Modulo.VIAJES, Modulo.NOVEDADES, Modulo.REPORTES);
            case SUPERVISOR_OPERACIONES:
                return EnumSet.of(Modulo.USUARIOS, Modulo.CONDUCTORES, Modulo.BUSES, Modulo.VIAJES, Modulo.NOVEDADES);
            case COORDINADOR_RUTAS:
                return EnumSet.of(Modulo.RUTAS, Modulo.VIAJES);
            case JEFE_FINANZAS:
                return EnumSet.of(Modulo.RECARGAS, Modulo.PAGOS);
            case ATENCION_CLIENTE:
                return EnumSet.of(Modulo.PASAJEROS, Modulo.NOVEDADES);
            default:
                return EnumSet.noneOf(Modulo.class);
        }
    }

    public static boolean puedeVer(Usuario u, Modulo m) {
        return u != null && u.estaActivo() && ve(u).contains(m);
    }

    public static boolean puedeEditar(Usuario u, Modulo m) {
        return u != null && u.estaActivo() && edita(u).contains(m);
    }

    /** Descripción corta de la responsabilidad del cargo, para mostrarla en pantalla. */
    public static String responsabilidad(Cargo cargo) {
        if (cargo == null) return "";
        switch (cargo) {
            case ADMINISTRADOR_PRINCIPAL: return "Acceso total y único que crea administradores.";
            case GERENTE_GENERAL: return "Dirige la operación: empresas, conductores, buses, rutas y viajes; consulta finanzas.";
            case SUPERVISOR_OPERACIONES: return "Aprueba conductores, asigna buses y programa viajes.";
            case COORDINADOR_RUTAS: return "Define rutas y programa los viajes.";
            case JEFE_FINANZAS: return "Aprueba recargas en efectivo, controla facturas y paga a los conductores.";
            case ATENCION_CLIENTE: return "Atiende pasajeros: consulta reservas y facturas y responde novedades.";
            default: return "";
        }
    }
}
