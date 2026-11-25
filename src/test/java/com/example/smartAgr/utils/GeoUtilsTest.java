package com.example.smartAgr.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    @Test
    void calculatesPolygonCenterFromStoredLatLngs() {
        String coordinates = "{\"shapeType\":\"polygon\",\"latlngs\":["
                + "{\"lat\":39.0,\"lng\":117.0},"
                + "{\"lat\":39.0,\"lng\":117.2},"
                + "{\"lat\":39.2,\"lng\":117.2},"
                + "{\"lat\":39.2,\"lng\":117.0}"
                + "]}";

        GeoUtils.LatLng center = GeoUtils.getCenter(coordinates, "polygon").orElseThrow();

        assertEquals(39.1, center.lat(), 0.000001);
        assertEquals(117.1, center.lng(), 0.000001);
    }

    @Test
    void calculatesDistanceInMeters() {
        GeoUtils.LatLng start = new GeoUtils.LatLng(39.0, 117.0);
        GeoUtils.LatLng end = new GeoUtils.LatLng(39.0, 117.01);

        double distance = GeoUtils.distanceMeters(start, end);

        assertTrue(distance > 850);
        assertTrue(distance < 875);
    }

    @Test
    void returnsEmptyCenterForInvalidCoordinates() {
        assertTrue(GeoUtils.getCenter("{bad-json", "polygon").isEmpty());
        assertTrue(GeoUtils.getCenter(null, "polygon").isEmpty());
    }
}
