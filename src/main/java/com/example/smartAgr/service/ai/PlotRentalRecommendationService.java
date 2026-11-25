package com.example.smartAgr.service.ai;

import com.example.smartAgr.dao.admin.AdminPlotDao;
import com.example.smartAgr.model.AdminPlot;
import com.example.smartAgr.utils.GeoUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PlotRentalRecommendationService {

    private static final String DEFAULT_STATUS = "available";
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int MAX_COMBINATIONS = 5;

    private final AdminPlotDao adminPlotDao;

    public PlotRentalRecommendationService(AdminPlotDao adminPlotDao) {
        this.adminPlotDao = adminPlotDao;
    }

    public SearchResult searchAvailablePlots(SearchRequest request) {
        String status = blankToDefault(request.status(), DEFAULT_STATUS);
        List<AdminPlot> matched = adminPlotDao.findAll().stream()
                .filter(plot -> matchesText(plot.getStatus(), status))
                .filter(plot -> matchesText(plot.getSoilType(), request.soilType()))
                .filter(plot -> matchesText(plot.getIrrigationType(), request.irrigationType()))
                .filter(plot -> matchesText(plot.getLandType(), request.landType()))
                .filter(plot -> matchesAddress(plot.getAddress(), request.addressKeyword()))
                .filter(plot -> matchesArea(plot.getArea(), request.minArea(), request.maxArea()))
                .sorted(Comparator.comparing(AdminPlot::getArea, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_SEARCH_RESULTS)
                .toList();

        List<PlotCandidate> candidates = matched.stream().map(this::toCandidate).toList();
        double totalArea = round2(matched.stream()
                .map(AdminPlot::getArea)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum());
        return new SearchResult(candidates.size(), totalArea, candidates);
    }

    public AdjacencyResult checkAdjacency(AdjacencyRequest request) {
        double maxDistance = request.maxDistance() == null ? 500.0 : request.maxDistance();
        List<AdminPlot> plots = adminPlotDao.findAllById(request.plotIds()).stream()
                .filter(plot -> plot.getArea() != null)
                .filter(plot -> GeoUtils.getCenter(plot.getCoordinates(), plot.getShapeType()).isPresent())
                .toList();

        List<PlotCandidate> singleMatches = plots.stream()
                .filter(plot -> request.targetArea() != null && plot.getArea() >= request.targetArea())
                .map(this::toCandidate)
                .toList();

        List<PlotCombination> combinations = new ArrayList<>();
        for (int i = 0; i < plots.size(); i++) {
            for (int j = i + 1; j < plots.size(); j++) {
                addPairCombination(plots.get(i), plots.get(j), request.targetArea(), maxDistance, combinations);
            }
        }
        for (int i = 0; i < plots.size(); i++) {
            for (int j = i + 1; j < plots.size(); j++) {
                for (int k = j + 1; k < plots.size(); k++) {
                    addTripleCombination(plots.get(i), plots.get(j), plots.get(k), request.targetArea(), maxDistance, combinations);
                }
            }
        }

        List<PlotCombination> bestCombinations = combinations.stream()
                .sorted(Comparator.comparing(PlotCombination::maxPairDistance)
                        .thenComparing(PlotCombination::totalArea))
                .limit(MAX_COMBINATIONS)
                .toList();

        return new AdjacencyResult(request.targetArea(), maxDistance, singleMatches, bestCombinations);
    }

    private void addPairCombination(AdminPlot first, AdminPlot second, Double targetArea,
                                    double maxDistance, List<PlotCombination> combinations) {
        GeoUtils.LatLng firstCenter = GeoUtils.getCenter(first.getCoordinates(), first.getShapeType()).orElseThrow();
        GeoUtils.LatLng secondCenter = GeoUtils.getCenter(second.getCoordinates(), second.getShapeType()).orElseThrow();
        double distance = GeoUtils.distanceMeters(firstCenter, secondCenter);
        double totalArea = first.getArea() + second.getArea();
        if (distance <= maxDistance && meetsTarget(totalArea, targetArea)) {
            combinations.add(new PlotCombination(
                    List.of(first.getId(), second.getId()),
                    List.of(first.getPlotName(), second.getPlotName()),
                    round2(totalArea),
                    round2(distance),
                    round2(distance),
                    "面积满足且地块距离较近"));
        }
    }

    private void addTripleCombination(AdminPlot first, AdminPlot second, AdminPlot third, Double targetArea,
                                      double maxDistance, List<PlotCombination> combinations) {
        GeoUtils.LatLng firstCenter = GeoUtils.getCenter(first.getCoordinates(), first.getShapeType()).orElseThrow();
        GeoUtils.LatLng secondCenter = GeoUtils.getCenter(second.getCoordinates(), second.getShapeType()).orElseThrow();
        GeoUtils.LatLng thirdCenter = GeoUtils.getCenter(third.getCoordinates(), third.getShapeType()).orElseThrow();
        double d12 = GeoUtils.distanceMeters(firstCenter, secondCenter);
        double d13 = GeoUtils.distanceMeters(firstCenter, thirdCenter);
        double d23 = GeoUtils.distanceMeters(secondCenter, thirdCenter);
        double maxPairDistance = Math.max(d12, Math.max(d13, d23));
        double avgDistance = (d12 + d13 + d23) / 3.0;
        double totalArea = first.getArea() + second.getArea() + third.getArea();
        if (maxPairDistance <= maxDistance && meetsTarget(totalArea, targetArea)) {
            combinations.add(new PlotCombination(
                    List.of(first.getId(), second.getId(), third.getId()),
                    List.of(first.getPlotName(), second.getPlotName(), third.getPlotName()),
                    round2(totalArea),
                    round2(maxPairDistance),
                    round2(avgDistance),
                    "三块地合计面积满足且整体距离在限制内"));
        }
    }

    private PlotCandidate toCandidate(AdminPlot plot) {
        boolean hasCoordinates = GeoUtils.getCenter(plot.getCoordinates(), plot.getShapeType()).isPresent();
        return new PlotCandidate(
                plot.getId(),
                plot.getPlotName(),
                plot.getArea(),
                plot.getCurrentCrop(),
                plot.getSoilType(),
                plot.getIrrigationType(),
                plot.getLandType(),
                plot.getAddress(),
                blankToDefault(plot.getStatus(), DEFAULT_STATUS),
                hasCoordinates);
    }

    private boolean matchesArea(Double area, Double minArea, Double maxArea) {
        if (area == null) {
            return false;
        }
        if (minArea != null && area < minArea) {
            return false;
        }
        return maxArea == null || area <= maxArea;
    }

    private boolean matchesText(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (actual == null || actual.isBlank()) {
            return false;
        }
        return actual.trim().equalsIgnoreCase(expected.trim());
    }

    private boolean matchesAddress(String actual, String expectedKeyword) {
        if (expectedKeyword == null || expectedKeyword.isBlank()) {
            return true;
        }
        return actual != null && actual.toLowerCase(Locale.ROOT).contains(expectedKeyword.trim().toLowerCase(Locale.ROOT));
    }

    private boolean meetsTarget(double totalArea, Double targetArea) {
        return targetArea == null || totalArea >= targetArea;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record SearchRequest(String crop, String soilType, String irrigationType, String landType,
                                Double minArea, Double maxArea, String addressKeyword, String status) {
    }

    public record SearchResult(int total, double totalArea, List<PlotCandidate> plots) {
    }

    public record PlotCandidate(Long id, String plotName, Double area, String currentCrop, String soilType,
                                String irrigationType, String landType, String address, String status,
                                boolean hasCoordinates) {
    }

    public record AdjacencyRequest(List<Long> plotIds, Double targetArea, Double maxDistance) {
    }

    public record AdjacencyResult(Double targetArea, double maxDistance, List<PlotCandidate> singleMatches,
                                  List<PlotCombination> combinations) {
    }

    public record PlotCombination(List<Long> plotIds, List<String> plotNames, double totalArea,
                                  double maxPairDistance, double avgDistance, String reason) {
    }
}
