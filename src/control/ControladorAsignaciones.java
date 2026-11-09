package control;

import fachadas.FachadaArchivos;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import objetosNegocio.Bus;
import objetosNegocio.Conductor;
import objetosNegocio.Ruta;
import objetosNegocio.Usuario;
import objetosServicio.Permisos;
import persistencia.AlmacenTexto;
import persistencia.Conductores;

/**
 * Bus y ruta asignados a cada conductor. Un conductor aprobado empieza SIN bus ni ruta; mientras no se le asignen no se le
 * puede programar ningún viaje. Cada bus se asigna a un solo conductor; una ruta puede compartirla varios.
 */
public class ControladorAsignaciones {
    private static final AlmacenTexto ALMACEN = new AlmacenTexto("asignaciones.dat");

    public static class Asignacion {
        private final String idConductor;
        private final String idBus;
        private final String idRuta;

        public Asignacion(String idConductor, String idBus, String idRuta) {
            this.idConductor = idConductor;
            this.idBus = idBus;
            this.idRuta = idRuta;
        }

        public String getIdConductor() { return idConductor; }
        public String getIdBus() { return idBus; }
        public String getIdRuta() { return idRuta; }
    }

    public Asignacion obtener(String idConductor) {
        try {
            for (String[] f : ALMACEN.leer()) {
                if (f[0].equals(idConductor)) {
                    return new Asignacion(f[0], f[1], f[2]);
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudieron leer las asignaciones: " + e.getMessage());
        }
        return null;
    }

    public List<Asignacion> listar() {
        List<Asignacion> lista = new ArrayList<>();
        try {
            for (String[] f : ALMACEN.leer()) {
                lista.add(new Asignacion(f[0], f[1], f[2]));
            }
        } catch (IOException e) {
            System.err.println("No se pudieron leer las asignaciones: " + e.getMessage());
        }
        return lista;
    }

    /** Texto para pantallas: "PBA-1021 · Quito → Ambato" o "Sin bus ni ruta asignados". */
    public String describir(String idConductor) {
        Asignacion a = obtener(idConductor);
        if (a == null) {
            return "Sin bus ni ruta asignados";
        }
        try {
            FachadaArchivos fachada = FachadaArchivos.getInstance();
            Bus b = fachada.obten(new Bus(a.getIdBus()));
            Ruta r = fachada.obten(new Ruta(a.getIdRuta()));
            return (b == null ? a.getIdBus() : b.getPlaca()) + " · " + (r == null ? a.getIdRuta() : r.getOrigen() + " → " + r.getDestino());
        } catch (Exception e) {
            return a.getIdBus() + " · " + a.getIdRuta();
        }
    }

    public synchronized ResultadoOperacion asignar(Usuario actor, String idConductor, String idBus, String idRuta) {
        if (!Permisos.puedeEditar(actor, Permisos.Modulo.CONDUCTORES)) {
            return new ResultadoOperacion(false, "Tu cargo no puede asignar buses y rutas a los conductores");
        }
        try {
            FachadaArchivos fachada = FachadaArchivos.getInstance();
            Conductor c = new Conductores("conductores.dat").obten(new Conductor(idConductor));
            Bus b = fachada.obten(new Bus(idBus));
            Ruta r = fachada.obten(new Ruta(idRuta));
            if (c == null || b == null || r == null) {
                return new ResultadoOperacion(false, "Conductor, bus o ruta no encontrados");
            }
            if (!"ACTIVO".equalsIgnoreCase(c.getEstado())) {
                return new ResultadoOperacion(false, "El conductor aún no está aprobado y activo");
            }
            if (!"ACTIVO".equalsIgnoreCase(b.getEstado())) {
                return new ResultadoOperacion(false, "El bus no está activo (" + b.getEstado() + ")");
            }
            String emp = c.getEmpresa() == null ? "" : c.getEmpresa().getIdEmpresa();
            if (b.getEmpresa() == null || !emp.equals(b.getEmpresa().getIdEmpresa()) || r.getEmpresa() == null || !emp.equals(r.getEmpresa().getIdEmpresa())) {
                return new ResultadoOperacion(false, "El conductor, el bus y la ruta deben ser de la misma empresa");
            }
            List<String[]> filas = new ArrayList<>();
            for (String[] f : ALMACEN.leer()) {
                if (f[0].equals(idConductor)) {
                    continue;
                }
                if (f[1].equals(idBus)) {
                    return new ResultadoOperacion(false, "Ese bus ya está asignado a otro conductor (" + f[0] + ")");
                }
                filas.add(f);
            }
            filas.add(new String[]{idConductor, idBus, idRuta});
            ALMACEN.reescribir(filas);
            return new ResultadoOperacion(true, "Asignado: " + c.getNombre() + " conducirá el bus " + b.getPlaca() + " en la ruta " + r.getOrigen() + " → " + r.getDestino());
        } catch (Exception e) {
            return new ResultadoOperacion(false, "No se pudo guardar la asignación: " + e.getMessage());
        }
    }

    public synchronized ResultadoOperacion quitar(Usuario actor, String idConductor) {
        if (!Permisos.puedeEditar(actor, Permisos.Modulo.CONDUCTORES)) {
            return new ResultadoOperacion(false, "Tu cargo no puede cambiar las asignaciones");
        }
        try {
            List<String[]> filas = new ArrayList<>();
            for (String[] f : ALMACEN.leer()) {
                if (!f[0].equals(idConductor)) {
                    filas.add(f);
                }
            }
            ALMACEN.reescribir(filas);
            return new ResultadoOperacion(true, "Asignación quitada");
        } catch (IOException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    /**
     * Regla del negocio para programar un viaje: el conductor principal debe estar activo y tener asignados el MISMO bus y la
     * MISMA ruta del viaje. Devuelve null si todo está bien o el mensaje del problema.
     */
    public String errorParaViaje(String idConductor, String idBus, String idRuta) {
        try {
            Conductor c = new Conductores("conductores.dat").obten(new Conductor(idConductor));
            if (c == null) {
                return "El conductor no existe";
            }
            if (!"ACTIVO".equalsIgnoreCase(c.getEstado())) {
                return "El conductor " + c.getNombre() + " no está activo";
            }
            Asignacion a = obtener(idConductor);
            if (a == null) {
                return c.getNombre() + " aún no tiene bus ni ruta asignados. Asígnalos primero en la pestaña Conductores.";
            }
            if (!a.getIdBus().equals(idBus) || !a.getIdRuta().equals(idRuta)) {
                return c.getNombre() + " tiene asignado: " + describir(idConductor) + ". El viaje debe usar ese mismo bus y esa misma ruta.";
            }
            return null;
        } catch (Exception e) {
            return "No se pudo verificar al conductor: " + e.getMessage();
        }
    }
}
