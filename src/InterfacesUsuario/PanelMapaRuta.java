package InterfacesUsuario;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;

/**
 * Mapa esquemático de Ecuador con el trayecto de una ruta resaltado. Usa la red vial de {@link DialogoFormularioRuta}.
 * El bus es directo: las ciudades marcadas en rojo son solo por donde PASA, no son paradas.
 */
public class PanelMapaRuta extends JPanel {
    private List<String> camino = new ArrayList<>();

    public PanelMapaRuta() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(520, 560));
    }

    static String clave(String ciudad) {
        return Normalizer.normalize(ciudad == null ? "" : ciudad, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase();
    }

    /** Calcula y muestra el trayecto entre dos ciudades. Devuelve las ciudades por las que pasa (sin origen ni destino). */
    public List<String> mostrar(String origen, String destino) {
        DialogoFormularioRuta.ResultadoRuta r = DialogoFormularioRuta.encontrarRutaMasCorta(clave(origen), clave(destino));
        camino = r == null ? new ArrayList<>() : new ArrayList<>(r.camino);
        repaint();
        List<String> intermedias = new ArrayList<>();
        for (int i = 1; i < camino.size() - 1; i++) {
            intermedias.add(camino.get(i));
        }
        return intermedias;
    }

    public boolean hayTrayecto() {
        return camino.size() > 1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Map<String, Point> coord = DialogoFormularioRuta.COORDENADAS;
        if (coord.isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Point p : coord.values()) {
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        minX -= 20; maxX += 20; minY -= 20; maxY += 20;
        double escala = Math.min((getWidth() - 40.0) / (maxX - minX), (getHeight() - 40.0) / (maxY - minY));
        int dx = (getWidth() - (int) ((maxX - minX) * escala)) / 2 - (int) (minX * escala);
        int dy = (getHeight() - (int) ((maxY - minY) * escala)) / 2 - (int) (minY * escala);
        AffineTransform anterior = g2.getTransform();
        g2.translate(dx, dy);
        g2.scale(escala, escala);
        g2.setColor(new Color(220, 220, 220));
        g2.setStroke(new BasicStroke(1f));
        for (Map.Entry<String, Map<String, Double>> e : DialogoFormularioRuta.GRAFO_VIAL.entrySet()) {
            Point p1 = coord.get(e.getKey());
            if (p1 == null) continue;
            for (String c2 : e.getValue().keySet()) {
                Point p2 = coord.get(c2);
                if (p2 != null) g2.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }
        if (camino.size() > 1) {
            g2.setColor(new Color(0x1B6CA8));
            g2.setStroke(new BasicStroke(3.5f));
            for (int i = 0; i < camino.size() - 1; i++) {
                Point p1 = coord.get(camino.get(i));
                Point p2 = coord.get(camino.get(i + 1));
                if (p1 != null && p2 != null) g2.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }
        for (Map.Entry<String, Point> e : coord.entrySet()) {
            Point p = e.getValue();
            boolean enRuta = camino.contains(e.getKey());
            boolean extremo = !camino.isEmpty() && (camino.get(0).equals(e.getKey()) || camino.get(camino.size() - 1).equals(e.getKey()));
            if (extremo) {
                g2.setColor(new Color(0, 128, 0));
                g2.fillOval(p.x - 6, p.y - 6, 12, 12);
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Arial", Font.BOLD, 12));
            } else if (enRuta) {
                g2.setColor(new Color(0xC0392B));
                g2.fillOval(p.x - 3, p.y - 3, 6, 6);
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Arial", Font.BOLD, 10));
            } else {
                g2.setColor(Color.GRAY);
                g2.fillOval(p.x - 2, p.y - 2, 4, 4);
                g2.setFont(new Font("Arial", Font.PLAIN, 9));
            }
            g2.drawString(e.getKey(), p.x + 6, p.y + 4);
        }
        dibujarSuperposicion(g2, coord);
        g2.setTransform(anterior);
    }

    /** Gancho para subclases: dibujar encima del mapa usando las coordenadas del mapa (ya con escala aplicada). */
    protected void dibujarSuperposicion(Graphics2D g2, Map<String, Point> coord) {
    }

    /** Ciudades del trayecto actual, de origen a destino. */
    protected List<String> getCamino() {
        return camino;
    }
}
