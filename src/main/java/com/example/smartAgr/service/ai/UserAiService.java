package com.example.smartAgr.service.ai;

import com.example.smartAgr.config.AiProperties;
import com.example.smartAgr.dao.user.UserPlotDao;
import com.example.smartAgr.model.UserPlot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserAiService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final UserPlotDao userPlotDao;
    private final ObjectMapper objectMapper;

    public UserAiService(ChatClient.Builder chatClientBuilder,
                         ChatMemory chatMemory,
                         VectorStore vectorStore,
                         UserPlotDao userPlotDao,
                         AiProperties aiProperties) {
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.userPlotDao = userPlotDao;
        this.objectMapper = new ObjectMapper();
        this.chatClient = chatClientBuilder
                .defaultSystem(aiProperties.getSystemPrompt())
                .build();
    }

    public Flux<String> chatStream(Long userId, String message) {
        String conversationId = "user:" + userId;
        return chatClient.prompt()
                .user(message)
                .tools(getUserPlotsTool(userId), getPlotDetailTool(userId), getPlotCountTool(userId))
                .advisors(new QuestionAnswerAdvisor(vectorStore),
                          new MessageChatMemoryAdvisor(chatMemory, conversationId, 20))
                .stream()
                .content();
    }

    public String chat(Long userId, String message) {
        String conversationId = "user:" + userId;
        return chatClient.prompt()
                .user(message)
                .tools(getUserPlotsTool(userId), getPlotDetailTool(userId), getPlotCountTool(userId))
                .advisors(new QuestionAnswerAdvisor(vectorStore),
                          new MessageChatMemoryAdvisor(chatMemory, conversationId, 20))
                .call()
                .content();
    }

    public void clearConversation(Long userId) {
        chatMemory.clear("user:" + userId);
    }

    // ==================== Tool 1: getUserPlots ====================
    private FunctionCallback getUserPlotsTool(Long userId) {
        return FunctionCallback.builder()
                .function("getUserPlots", (GetUserPlotsRequest req) -> toJson(buildPlotList(userId)))
                .description("获取当前用户的所有地块信息，包含地块名称、面积、当前作物、上季作物、土壤类型、灌溉方式、土地类型、地址等")
                .inputType(GetUserPlotsRequest.class)
                .build();
    }

    private Map<String, Object> buildPlotList(Long userId) {
        List<UserPlot> plots = userPlotDao.findByUserId(userId);
        List<Map<String, Object>> simplified = plots.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("plotName", p.getPlotName());
            m.put("area", p.getArea());
            m.put("currentCrop", p.getCurrentCrop());
            m.put("lastCrop", p.getLastCrop());
            m.put("soilType", p.getSoilType());
            m.put("irrigationType", p.getIrrigationType());
            m.put("landType", p.getLandType());
            m.put("address", p.getAddress());
            return m;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", simplified.size());
        result.put("plots", simplified);
        return result;
    }

    // ==================== Tool 2: getPlotDetail ====================
    private FunctionCallback getPlotDetailTool(Long userId) {
        return FunctionCallback.builder()
                .function("getPlotDetail", (GetPlotDetailRequest req) -> {
                    UserPlot plot = userPlotDao.findById(req.plotId).orElse(null);
                    if (plot == null || !plot.getUserId().equals(userId)) {
                        return toJson(Map.of("error", "地块不存在或无权访问"));
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", plot.getId());
                    m.put("plotName", plot.getPlotName());
                    m.put("area", plot.getArea());
                    m.put("currentCrop", plot.getCurrentCrop());
                    m.put("lastCrop", plot.getLastCrop());
                    m.put("soilType", plot.getSoilType());
                    m.put("irrigationType", plot.getIrrigationType());
                    m.put("landType", plot.getLandType());
                    m.put("shapeType", plot.getShapeType());
                    m.put("address", plot.getAddress());
                    m.put("contactPerson", plot.getContactPerson());
                    m.put("phone", plot.getPhone());
                    m.put("createTime", plot.getCreateTime() != null ? plot.getCreateTime().toString() : null);
                    return toJson(m);
                })
                .description("根据地块ID查询单个地块的详细信息，包含完整的土壤、作物、灌溉、坐标等信息。用户提到具体地块编号时必须调用。")
                .inputType(GetPlotDetailRequest.class)
                .build();
    }

    // ==================== Tool 3: getPlotCount ====================
    private FunctionCallback getPlotCountTool(Long userId) {
        return FunctionCallback.builder()
                .function("getPlotCount", (GetPlotCountRequest req) -> {
                    List<UserPlot> plots = userPlotDao.findByUserId(userId);
                    long count = plots.size();
                    double totalArea = plots.stream()
                            .filter(p -> p.getArea() != null)
                            .mapToDouble(UserPlot::getArea)
                            .sum();
                    return toJson(Map.of("count", count, "totalArea", Math.round(totalArea * 100.0) / 100.0));
                })
                .description("获取用户的地块总数和总面积统计")
                .inputType(GetPlotCountRequest.class)
                .build();
    }

    // ==================== Tool input DTOs ====================
    public record GetUserPlotsRequest() {}
    public record GetPlotDetailRequest(Long plotId) {}
    public record GetPlotCountRequest() {}

    // ==================== util ====================
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\": \"序列化失败\"}";
        }
    }
}
