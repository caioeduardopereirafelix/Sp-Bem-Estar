package com.br.spbemestar.dto;

import com.br.spbemestar.exception.InvalidBoundingBoxException;

public record BoundingBox(double minLon, double minLat, double maxLon, double maxLat) {

    public BoundingBox {
        if (minLon < -180 || maxLon > 180 || minLat < -90 || maxLat > 90) {
            throw new InvalidBoundingBoxException("Coordenadas fora dos limites válidos");
        }
        if (minLon >= maxLon || minLat >= maxLat) {
            throw new InvalidBoundingBoxException("O mínimo precisa ser menor que o máximo");
        }
    }

    /**
     * Formato: minLon,minLat,maxLon,maxLat (mesma ordem do GeoJSON e do OpenStreetMap).
     */
    public static BoundingBox parse(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            throw new InvalidBoundingBoxException("Use o formato minLon,minLat,maxLon,maxLat");
        }
        try {
            return new BoundingBox(
                    Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException e) {
            throw new InvalidBoundingBoxException("Os quatro valores precisam ser números");
        }
    }
}
