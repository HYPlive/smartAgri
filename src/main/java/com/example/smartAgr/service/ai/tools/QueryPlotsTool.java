package com.example.smartAgr.service.ai.tools;

import com.example.smartAgr.dao.admin.AdminPlotDao;
import com.example.smartAgr.model.AdminPlot;
import com.example.smartAgr.service.ai.AgentTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class QueryPlotsTool implements AgentTool {

    private final AdminPlotDao plotDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "query_plots";
    }

    @Override
    public String getDescription() {
        return "查询管理员端农业地块信息。可按地区(region)、作物(crop_type)、地块名称(name)、土壤类型(soil_type)、灌溉方式(irrigation_type)、土地类型(land_type)、地址(address)、面积范围(min_area/max_area)筛选。返回地块详情和统计信息。";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();

        addStringProperty(properties, "region", "地区名称，如：西青区、武清区");
        addStringProperty(properties, "crop_type", "当前作物类型，如：玉米、土豆、小麦");
        addStringProperty(properties, "name", "地块名称关键词");
        addStringProperty(properties, "soil_type", "土壤类型，如：壤土、沙壤土、黏土");
        addStringProperty(properties, "irrigation_type", "灌溉方式，如：喷灌、滴灌、渠灌");
        addStringProperty(properties, "land_type", "土地类型，如：租赁、自有");
        addStringProperty(properties, "address", "地址关键词");
        addNumberProperty(properties, "min_area", "最小面积，单位亩");
        addNumberProperty(properties, "max_area", "最大面积，单位亩");

        schema.set("properties", properties);
        return schema;
    }

    @Override
    public String execute(JsonNode parameters) throws Exception {
        List<AdminPlot> plots = plotDao.findAll();

        String region = textParam(parameters, "region");
        String cropType = textParam(parameters, "crop_type");
        String name = textParam(parameters, "name");
        String soilType = textParam(parameters, "soil_type");
        String irrigationType = textParam(parameters, "irrigation_type");
        String landType = textParam(parameters, "land_type");
        String address = textParam(parameters, "address");
        Double minArea = doubleParam(parameters, "min_area");
        Double maxArea = doubleParam(parameters, "max_area");

        ArrayNode result = objectMapper.createArrayNode();
        double totalArea = 0.0;
        double maxAreaValue = 0.0;
        double minAreaValue = 0.0;
        boolean hasArea = false;
        Map<String, Integer> cropCounts = new HashMap<>();
        Map<String, Integer> regionCounts = new HashMap<>();

        for (AdminPlot plot : plots) {
            if (!matchesLocation(plot, region)
                    || !matches(plot.getCurrentCrop(), cropType)
                    || !matches(plot.getPlotName(), name)
                    || !matches(plot.getSoilType(), soilType)
                    || !matches(plot.getIrrigationType(), irrigationType)
                    || !matches(plot.getLandType(), landType)
                    || !matches(plot.getAddress(), address)) {
                continue;
            }
            if (minArea != null && (plot.getArea() == null || plot.getArea() < minArea)) {
                continue;
            }
            if (maxArea != null && (plot.getArea() == null || plot.getArea() > maxArea)) {
                continue;
            }

            result.add(toJson(plot));

            if (plot.getArea() != null) {
                totalArea += plot.getArea();
                maxAreaValue = hasArea ? Math.max(maxAreaValue, plot.getArea()) : plot.getArea();
                minAreaValue = hasArea ? Math.min(minAreaValue, plot.getArea()) : plot.getArea();
                hasArea = true;
            }
            increment(cropCounts, plot.getCurrentCrop());
            increment(regionCounts, plot.getRegion());
        }

        ObjectNode statistics = objectMapper.createObjectNode();
        statistics.put("totalArea", totalArea);
        statistics.put("maxArea", hasArea ? maxAreaValue : 0.0);
        statistics.put("minArea", hasArea ? minAreaValue : 0.0);
        statistics.set("cropCounts", objectMapper.valueToTree(cropCounts));
        statistics.set("regionCounts", objectMapper.valueToTree(regionCounts));

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("total", result.size());
        wrapper.set("plots", result);
        wrapper.set("statistics", statistics);
        return objectMapper.writeValueAsString(wrapper);
    }

    private ObjectNode toJson(AdminPlot plot) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", plot.getId());
        node.put("name", plot.getPlotName());
        node.put("area", plot.getArea());
        node.put("lastCrop", plot.getLastCrop());
        node.put("currentCrop", plot.getCurrentCrop());
        node.put("region", plot.getRegion());
        node.put("soilType", plot.getSoilType());
        node.put("irrigationType", plot.getIrrigationType());
        node.put("landType", plot.getLandType());
        node.put("address", plot.getAddress());
        node.put("contactPerson", plot.getContactPerson());
        node.put("phone", plot.getPhone());
        return node;
    }

    private void addStringProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = objectMapper.createObjectNode();
        property.put("type", "string");
        property.put("description", description);
        properties.set(name, property);
    }

    private void addNumberProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = objectMapper.createObjectNode();
        property.put("type", "number");
        property.put("description", description);
        properties.set(name, property);
    }

    private String textParam(JsonNode parameters, String name) {
        if (parameters == null || !parameters.has(name) || parameters.get(name).isNull()) {
            return null;
        }
        String value = parameters.get(name).asText();
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private Double doubleParam(JsonNode parameters, String name) {
        if (parameters == null || !parameters.has(name) || parameters.get(name).isNull()) {
            return null;
        }
        return parameters.get(name).asDouble();
    }

    private boolean matches(String actual, String expectedKeyword) {
        return expectedKeyword == null || (actual != null && actual.contains(expectedKeyword));
    }

    private boolean matchesLocation(AdminPlot plot, String expectedKeyword) {
        return expectedKeyword == null
                || matches(plot.getRegion(), expectedKeyword)
                || matches(plot.getAddress(), expectedKeyword);
    }

    private void increment(Map<String, Integer> counts, String key) {
        if (key == null || key.trim().isEmpty()) {
            key = "未知";
        }
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }
}
