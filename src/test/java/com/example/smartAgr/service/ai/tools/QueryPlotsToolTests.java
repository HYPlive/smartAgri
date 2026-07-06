package com.example.smartAgr.service.ai.tools;

import com.example.smartAgr.dao.admin.AdminPlotDao;
import com.example.smartAgr.model.AdminPlot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryPlotsToolTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void filtersAdminPlotsByMultipleConditionsAndReturnsDetailFields() throws Exception {
        AdminPlotDao dao = mock(AdminPlotDao.class);
        when(dao.findAll()).thenReturn(Arrays.asList(
                plot(1L, "一号地", "玉米", "土豆", "张三", "13800000000", "沙壤土", "喷灌", "租赁", 120.0, "天津西青", "西青区"),
                plot(2L, "二号地", "小麦", "玉米", "李四", "13900000000", "黏土", "滴灌", "自有", 60.0, "天津武清", "武清区")
        ));

        QueryPlotsTool tool = new QueryPlotsTool(dao);
        JsonNode params = objectMapper.readTree("{\"crop_type\":\"土豆\",\"soil_type\":\"沙壤土\",\"min_area\":100}");

        JsonNode result = objectMapper.readTree(tool.execute(params));

        assertEquals(1, result.get("total").asInt());
        JsonNode first = result.get("plots").get(0);
        assertEquals("一号地", first.get("name").asText());
        assertEquals("玉米", first.get("lastCrop").asText());
        assertEquals("租赁", first.get("landType").asText());
        assertEquals("天津西青", first.get("address").asText());
    }

    @Test
    void returnsStatisticsForMatchedAdminPlots() throws Exception {
        AdminPlotDao dao = mock(AdminPlotDao.class);
        when(dao.findAll()).thenReturn(Arrays.asList(
                plot(1L, "一号地", "玉米", "土豆", "张三", "13800000000", "沙壤土", "喷灌", "租赁", 120.0, "天津西青", "西青区"),
                plot(2L, "二号地", "小麦", "土豆", "李四", "13900000000", "沙壤土", "滴灌", "自有", 80.0, "天津西青", "西青区")
        ));

        QueryPlotsTool tool = new QueryPlotsTool(dao);
        JsonNode result = objectMapper.readTree(tool.execute(objectMapper.readTree("{\"region\":\"西青区\"}")));

        assertEquals(2, result.get("total").asInt());
        assertEquals(200.0, result.get("statistics").get("totalArea").asDouble(), 0.001);
        assertEquals(120.0, result.get("statistics").get("maxArea").asDouble(), 0.001);
        assertTrue(result.get("statistics").get("cropCounts").has("土豆"));
        assertEquals(2, result.get("statistics").get("cropCounts").get("土豆").asInt());
    }

    @Test
    void regionKeywordAlsoMatchesAddress() throws Exception {
        AdminPlotDao dao = mock(AdminPlotDao.class);
        when(dao.findAll()).thenReturn(Arrays.asList(
                plot(1031L, "test1", "玉米", "土豆", "hyp", "17822092202", "砂质土", "渠灌", "开垦荒地",
                        397.92, "河北省张家口市沽源县小河子乡二秦高速", null)
        ));

        QueryPlotsTool tool = new QueryPlotsTool(dao);
        JsonNode result = objectMapper.readTree(tool.execute(objectMapper.readTree("{\"region\":\"沽源\"}")));

        assertEquals(1, result.get("total").asInt());
        assertEquals("test1", result.get("plots").get(0).get("name").asText());
        assertEquals("河北省张家口市沽源县小河子乡二秦高速", result.get("plots").get(0).get("address").asText());
    }

    private AdminPlot plot(Long id, String name, String lastCrop, String currentCrop,
                           String contactPerson, String phone, String soilType,
                           String irrigationType, String landType, Double area,
                           String address, String region) {
        AdminPlot plot = new AdminPlot();
        plot.setId(id);
        plot.setPlotName(name);
        plot.setLastCrop(lastCrop);
        plot.setCurrentCrop(currentCrop);
        plot.setContactPerson(contactPerson);
        plot.setPhone(phone);
        plot.setSoilType(soilType);
        plot.setIrrigationType(irrigationType);
        plot.setLandType(landType);
        plot.setArea(area);
        plot.setAddress(address);
        plot.setRegion(region);
        return plot;
    }
}
