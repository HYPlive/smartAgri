package com.example.smartAgr.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

public final class GeoUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private GeoUtils() {
    }

    public record LatLng(double lat, double lng) {
    }

    public static Optional<LatLng> getCenter(String coordinates, String shapeType) {
        if (coordinates == null || coordinates.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(coordinates);
            String resolvedShapeType = shapeType;
            if ((resolvedShapeType == null || resolvedShapeType.isBlank()) && root.hasNonNull("shapeType")) {
                resolvedShapeType = root.get("shapeType").asText();
            }

            if ("circle".equalsIgnoreCase(resolvedShapeType)) {
                JsonNode center = root.get("center");
                if (center == null || !center.hasNonNull("lat") || !center.hasNonNull("lng")) {
                    return Optional.empty();
                }
                return Optional.of(new LatLng(center.get("lat").asDouble(), center.get("lng").asDouble()));
            }

            if ("polygon".equalsIgnoreCase(resolvedShapeType)) {
                JsonNode latlngs = root.get("latlngs");
                if (latlngs == null || !latlngs.isArray() || latlngs.isEmpty()) {
                    return Optional.empty();
                }

                double latSum = 0;
                double lngSum = 0;
                int count = 0;
                for (JsonNode point : latlngs) {
                    if (point.hasNonNull("lat") && point.hasNonNull("lng")) {
                        latSum += point.get("lat").asDouble();
                        lngSum += point.get("lng").asDouble();
                        count++;
                    }
                }
                if (count == 0) {
                    return Optional.empty();
                }
                return Optional.of(new LatLng(latSum / count, lngSum / count));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    public static double distanceMeters(LatLng a, LatLng b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());

        double haversine = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double centralAngle = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
        return EARTH_RADIUS_METERS * centralAngle;
    }
}
