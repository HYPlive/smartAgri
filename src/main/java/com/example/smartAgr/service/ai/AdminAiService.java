package com.example.smartAgr.service.ai;

import com.example.smartAgr.config.AiProperties;
import com.example.smartAgr.dao.admin.AdminPlotDao;
import com.example.smartAgr.model.AdminPlot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminAiService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final AdminPlotDao adminPlotDao;
    private final PlotRentalRecommendationService rentalRecommendationService;
    private final ObjectMapper objectMapper;

    public AdminAiService(ChatClient.Builder chatClientBuilder,
                          ChatMemory chatMemory,
                          VectorStore vectorStore,
                          AdminPlotDao adminPlotDao,
                          PlotRentalRecommendationService rentalRecommendationService,
                          AiProperties aiProperties) {
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.adminPlotDao = adminPlotDao;
        this.rentalRecommendationService = rentalRecommendationService;
        this.objectMapper = new ObjectMapper();
        this.chatClient = chatClientBuilder
                .defaultSystem(aiProperties.getAdminSystemPrompt())
                .defaultTools(getAllPlotsTool(), getPlotDetailTool(), getPlotStatisticsTool(), queryPlotsTool(),
                        searchAvailablePlotsTool(), checkAdjacencyTool())
                .build();
    }

    public Flux<String> chatStream(Long adminId, String message) {
        String conversationId = "admin:" + adminId;
        return chatClient.prompt()
                .user(message)
                .advisors(new QuestionAnswerAdvisor(vectorStore),
                          new MessageChatMemoryAdvisor(chatMemory, conversationId, 20))
                .stream()
                .content();
    }

    public String chat(Long adminId, String message) {
        String conversationId = "admin:" + adminId;
        return chatClient.prompt()
                .user(message)
                .advisors(new QuestionAnswerAdvisor(vectorStore),
                          new MessageChatMemoryAdvisor(chatMemory, conversationId, 20))
                .call()
                .content();
    }

    public void clearConversation(Long adminId) {
        chatMemory.clear("admin:" + adminId);
    }

    // ==================== Tool 1: getAllPlots ====================
    private FunctionCallback getAllPlotsTool() {
        return FunctionCallback.builder()
                .function("getAllPlots", (GetAllPlotsRequest req) -> {
                    List<AdminPlot> all = adminPlotDao.findAll();
                    List<Map<String, Object>> list = all.stream().map(this::buildPlotSummary).toList();
                    return toJson(Map.of("total", list.size(), "plots", list));
                })
                .description("获取系统中所有地块的列表，包含地块ID、名称、面积、当前作物、土壤类型、灌溉方式、土地类型和地址")
                .inputType(GetAllPlotsRequest.class)
                .build();
    }

    // ==================== Tool 2: getPlotDetail ====================
    private FunctionCallback getPlotDetailTool() {
        return FunctionCallback.builder()
                .function("getPlotDetail", (GetPlotDetailRequest req) -> {
                    AdminPlot plot = adminPlotDao.findById(req.plotId).orElse(null);
                    if (plot == null) return toJson(Map.of("error", "地块不存在"));
                    return toJson(buildPlotFull(plot));
                })
                .description("根据地块ID查询单个地块的完整详细信息")
                .inputType(GetPlotDetailRequest.class)
                .build();
    }

    // ==================== Tool 3: getPlotStatistics ====================
    private FunctionCallback getPlotStatisticsTool() {
        return FunctionCallback.builder()
                .function("getPlotStatistics", (GetPlotStatisticsRequest req) -> {
                    List<AdminPlot> all = adminPlotDao.findAll();
                    long count = all.size();
                    double totalArea = all.stream().filter(p -> p.getArea() != null).mapToDouble(AdminPlot::getArea).sum();

                    Map<String, Long> byCrop = all.stream()
                            .filter(p -> p.getCurrentCrop() != null && !p.getCurrentCrop().isEmpty())
                            .collect(Collectors.groupingBy(AdminPlot::getCurrentCrop, Collectors.counting()));

                    Map<String, Long> bySoil = all.stream()
                            .filter(p -> p.getSoilType() != null && !p.getSoilType().isEmpty())
                            .collect(Collectors.groupingBy(AdminPlot::getSoilType, Collectors.counting()));

                    Map<String, Long> byIrrigation = all.stream()
                            .filter(p -> p.getIrrigationType() != null && !p.getIrrigationType().isEmpty())
                            .collect(Collectors.groupingBy(AdminPlot::getIrrigationType, Collectors.counting()));

                    Map<String, Double> areaByCrop = new LinkedHashMap<>();
                    for (AdminPlot p : all) {
                        if (p.getCurrentCrop() != null && !p.getCurrentCrop().isEmpty() && p.getArea() != null) {
                            areaByCrop.merge(p.getCurrentCrop(), p.getArea(), Double::sum);
                        }
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("totalPlots", count);
                    result.put("totalArea", Math.round(totalArea * 100.0) / 100.0);
                    result.put("plotsByCrop", byCrop);
                    result.put("areaByCrop", areaByCrop);
                    result.put("plotsBySoil", bySoil);
                    result.put("plotsByIrrigation", byIrrigation);
                    return toJson(result);
                })
                .description("获取全系统地块的统计数据：地块总数、总面积（亩）、按作物分类数量及面积、按土壤类型分类、按灌溉方式分类")
                .inputType(GetPlotStatisticsRequest.class)
                .build();
    }

    // ==================== Tool 4: queryPlots ====================
    private FunctionCallback queryPlotsTool() {
        return FunctionCallback.builder()
                .function("queryPlots", (QueryPlotsRequest req) -> {
                    List<AdminPlot> all = adminPlotDao.findAll();

                    if (req.crop != null && !req.crop.isEmpty()) {
                        all = all.stream().filter(p -> req.crop.equals(p.getCurrentCrop())).toList();
                    }
                    if (req.soilType != null && !req.soilType.isEmpty()) {
                        all = all.stream().filter(p -> req.soilType.equals(p.getSoilType())).toList();
                    }
                    if (req.irrigationType != null && !req.irrigationType.isEmpty()) {
                        all = all.stream().filter(p -> req.irrigationType.equals(p.getIrrigationType())).toList();
                    }
                    if (req.landType != null && !req.landType.isEmpty()) {
                        all = all.stream().filter(p -> req.landType.equals(p.getLandType())).toList();
                    }
                    if (req.minArea != null) {
                        all = all.stream().filter(p -> p.getArea() != null && p.getArea() >= req.minArea).toList();
                    }
                    if (req.maxArea != null) {
                        all = all.stream().filter(p -> p.getArea() != null && p.getArea() <= req.maxArea).toList();
                    }

                    List<Map<String, Object>> list = all.stream().map(this::buildPlotSummary).toList();
                    double sumArea = all.stream().filter(p -> p.getArea() != null).mapToDouble(AdminPlot::getArea).sum();
                    return toJson(Map.of("total", list.size(), "totalArea", Math.round(sumArea * 100.0) / 100.0, "plots", list));
                })
                .description("按条件筛选地块。支持的条件：crop(作物类型)、soilType(土壤类型)、irrigationType(灌溉方式)、landType(土地类型)、minArea(最小面积/亩)、maxArea(最大面积/亩)。所有条件可自由组合，留空表示不限。")
                .inputType(QueryPlotsRequest.class)
                .build();
    }

    // ==================== Tool 5: searchAvailablePlots ====================
    private FunctionCallback searchAvailablePlotsTool() {
        return FunctionCallback.builder()
                .function("searchAvailablePlots", (SearchAvailablePlotsRequest req) -> {
                    PlotRentalRecommendationService.SearchResult result = rentalRecommendationService.searchAvailablePlots(
                            new PlotRentalRecommendationService.SearchRequest(
                                    req.crop,
                                    req.soilType,
                                    req.irrigationType,
                                    req.landType,
                                    req.minArea,
                                    req.maxArea,
                                    req.addressKeyword,
                                    req.status));
                    return toJson(result);
                })
                .description("筛选可租赁地块。用于用户提出租地、租赁、找地、承包、连片地、推荐地块等需求时。支持条件：crop 作物意向、soilType 土壤、irrigationType 灌溉、landType 土地类型、minArea 最小面积、maxArea 最大面积、addressKeyword 地址关键词、status 状态，默认只查 available。")
                .inputType(SearchAvailablePlotsRequest.class)
                .build();
    }

    // ==================== Tool 6: checkAdjacency ====================
    private FunctionCallback checkAdjacencyTool() {
        return FunctionCallback.builder()
                .function("checkAdjacency", (CheckAdjacencyRequest req) -> {
                    PlotRentalRecommendationService.AdjacencyResult result = rentalRecommendationService.checkAdjacency(
                            new PlotRentalRecommendationService.AdjacencyRequest(req.plotIds, req.targetArea, req.maxDistance));
                    return toJson(result);
                })
                .description("计算候选地块之间的距离和组合面积。searchAvailablePlots 返回多个候选地块且用户要求连片、相邻、距离近、组合面积满足目标时调用。maxDistance 默认 500 米。")
                .inputType(CheckAdjacencyRequest.class)
                .build();
    }

    // ==================== util ====================
    private Map<String, Object> buildPlotSummary(AdminPlot p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("plotName", p.getPlotName());
        m.put("area", p.getArea());
        m.put("currentCrop", p.getCurrentCrop());
        m.put("soilType", p.getSoilType());
        m.put("irrigationType", p.getIrrigationType());
        m.put("landType", p.getLandType());
        m.put("address", p.getAddress());
        m.put("status", p.getStatus());
        return m;
    }

    private Map<String, Object> buildPlotFull(AdminPlot p) {
        Map<String, Object> m = buildPlotSummary(p);
        m.put("lastCrop", p.getLastCrop());
        m.put("contactPerson", p.getContactPerson());
        m.put("phone", p.getPhone());
        m.put("shapeType", p.getShapeType());
        return m;
    }

    public record GetAllPlotsRequest() {}
    public record GetPlotDetailRequest(Long plotId) {}
    public record GetPlotStatisticsRequest() {}
    public record QueryPlotsRequest(String crop, String soilType, String irrigationType, String landType, Double minArea, Double maxArea) {}
    public record SearchAvailablePlotsRequest(String crop, String soilType, String irrigationType, String landType,
                                              Double minArea, Double maxArea, String addressKeyword, String status) {}
    public record CheckAdjacencyRequest(List<Long> plotIds, Double targetArea, Double maxDistance) {}

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\": \"序列化失败\"}";
        }
    }
}
