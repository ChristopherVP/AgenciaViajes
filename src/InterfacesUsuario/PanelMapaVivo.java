package InterfacesUsuario;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;

/** Mapa con un bus que avanza por el trayecto según el progreso (0 a 1) de la simulación del viaje. */
public class PanelMapaVivo extends PanelMapaRuta {
    private double progreso;

    public void setProgreso(double valor) {
        this.progreso = Math.max(0, Math.min(1, valor));
        repaint();
    }

    /** Punto del mapa donde va el bus: se reparte el progreso según la longitud de cada tramo del camino. */
    static Point2D.Double posicion(List<String> camino, Map<String, Point> coord, double progreso) {
        if (camino.size() < 2) {
            return null;
        }
        double total = 0;
        for (int i = 0; i < camino.size() - 1; i++) {
            total += tramo(camino.get(i), camino.get(i + 1), coord);
        }
        double objetivo = total * progreso;
        for (int i = 0; i < camino.size() - 1; i++) {
            Point a = coord.get(camino.get(i));
            Point b = coord.get(camino.get(i + 1));
            if (a == null || b == null) {
                continue;
            }
            double largo = a.distance(b);
            if (objetivo <= largo || i == camino.size() - 2) {
                double f = largo == 0 ? 0 : Math.min(1, objetivo / largo);
                return new Point2D.Double(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f);
            }
            objetivo -= largo;
        }
        return null;
    }

    private static double tramo(String a, String b, Map<String, Point> coord) {
        Point p = coord.get(a);
        Point q = coord.get(b);
        return p == null || q == null ? 0 : p.distance(q);
    }

    @Override
    protected void dibujarSuperposicion(Graphics2D g2, Map<String, Point> coord) {
        Point2D.Double pos = posicion(getCamino(), coord, progreso);
        if (pos == null) {
            return;
        }
        int x = (int) Math.round(pos.x);
        int y = (int) Math.round(pos.y);
        g2.setColor(new Color(255, 193, 7, 90));
        g2.fillOval(x - 14, y - 14, 28, 28);
        g2.setColor(new Color(0xE67E00));
        g2.fillOval(x - 7, y - 7, 14, 14);
        g2.setColor(Color.WHITE);
        g2.drawOval(x - 7, y - 7, 14, 14);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Arial", Font.BOLD, 11));
        g2.drawString("BUS " + Math.round(progreso * 100) + "%", x + 10, y - 8);
    }
}
