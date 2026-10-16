package com.br.spbemestar.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoPoints {

    public static final int WGS84 = 4326;

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), WGS84);

    private GeoPoints() {
    }

    /**
     * Cria um ponto WGS84. Atenção à ordem: no JTS, x é a longitude e y é a latitude.
     */
    public static Point of(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude fora do intervalo [-90, 90]: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude fora do intervalo [-180, 180]: " + longitude);
        }
        return FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    public static GeometryFactory factory() {
        return FACTORY;
    }
}
