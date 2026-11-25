package com.example.smartAgr.service.ai;

import com.example.smartAgr.dao.admin.AdminPlotDao;
import com.example.smartAgr.model.AdminPlot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlotRentalRecommendationServiceTest {

    private final AdminPlotDao adminPlotDao = mock(AdminPlotDao.class);
    private final PlotRentalRecommendationService service = new PlotRentalRecommendationService(adminPlotDao);

    @Test
    void searchAvailablePlotsFiltersByStatusSoilIrrigationAndArea() {
        AdminPlot match = plot(1L, "张北一号地", 60.0, "壤土", "渠灌", "租赁", "available",
                polygon(39.0, 117.0));
        AdminPlot leased = plot(2L, "张北二号地", 80.0, "壤土", "渠灌", "租赁", "leased",
                polygon(39.001, 117.001));
        AdminPlot wrongSoil = plot(3L, "张北三号地", 90.0, "砂质土", "渠灌", "租赁", "available",
                polygon(39.002, 117.002));
        when(adminPlotDao.findAll()).thenReturn(List.of(match, leased, wrongSoil));

        PlotRentalRecommendationService.SearchResult result = service.searchAvailablePlots(
                new PlotRentalRecommendationService.SearchRequest(
                        "甜菜", "壤土", "渠灌", "租赁", 50.0, null, null, null));

        assertEquals(1, result.total());
        assertEquals(60.0, result.totalArea(), 0.001);
        assertEquals("张北一号地", result.plots().get(0).plotName());
        assertTrue(result.plots().get(0).hasCoordinates());
    }

    @Test
    void checkAdjacencyReturnsNearbyCombinationMeetingTargetArea() {
        AdminPlot first = plot(1L, "张北一号地", 45.0, "壤土", "渠灌", "租赁", "available",
                polygon(39.0, 117.0));
        AdminPlot second = plot(2L, "张北二号地", 42.0, "壤土", "渠灌", "租赁", "available",
                polygon(39.0, 117.002));
        AdminPlot far = plot(3L, "远处地块", 50.0, "壤土", "渠灌", "租赁", "available",
                polygon(39.0, 117.02));
        when(adminPlotDao.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(first, second, far));

        PlotRentalRecommendationService.AdjacencyResult result = service.checkAdjacency(
                new PlotRentalRecommendationService.AdjacencyRequest(List.of(1L, 2L, 3L), 80.0, 500.0));

        assertEquals(0, result.singleMatches().size());
        assertEquals(1, result.combinations().size());
        PlotRentalRecommendationService.PlotCombination combination = result.combinations().get(0);
        assertEquals(List.of(1L, 2L), combination.plotIds());
        assertEquals(87.0, combination.totalArea(), 0.001);
        assertTrue(combination.maxPairDistance() < 200);
    }

    private AdminPlot plot(Long id, String name, Double area, String soilType, String irrigationType,
                           String landType, String status, String coordinates) {
        AdminPlot plot = new AdminPlot();
        plot.setId(id);
        plot.setPlotName(name);
        plot.setArea(area);
        plot.setSoilType(soilType);
        plot.setIrrigationType(irrigationType);
        plot.setLandType(landType);
        plot.setStatus(status);
        plot.setShapeType("polygon");
        plot.setCoordinates(coordinates);
        plot.setAddress("河北省张家口市张北县");
        return plot;
    }

    private String polygon(double lat, double lng) {
        return "{\"shapeType\":\"polygon\",\"latlngs\":["
                + "{\"lat\":" + lat + ",\"lng\":" + lng + "},"
                + "{\"lat\":" + lat + ",\"lng\":" + (lng + 0.0001) + "},"
                + "{\"lat\":" + (lat + 0.0001) + ",\"lng\":" + (lng + 0.0001) + "},"
                + "{\"lat\":" + (lat + 0.0001) + ",\"lng\":" + lng + "}"
                + "]}";
    }
}
