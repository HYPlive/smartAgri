package com.example.smartAgr.controller.admin;

import com.example.smartAgr.context.BaseContext;
import com.example.smartAgr.result.Result;
import com.example.smartAgr.service.ai.AdminAiService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/admin/ai")
public class AdminAiController {

    private final AdminAiService adminAiService;

    public AdminAiController(AdminAiService adminAiService) {
        this.adminAiService = adminAiService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> request) {
        Long adminId = BaseContext.getCurrentId();
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            SseEmitter errorEmitter = new SseEmitter();
            errorEmitter.completeWithError(new IllegalArgumentException("消息不能为空"));
            return errorEmitter;
        }

        SseEmitter emitter = new SseEmitter(300000L);

        adminAiService.chatStream(adminId, message)
                .doOnComplete(() -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    } catch (Exception ignored) {}
                    emitter.complete();
                })
                .doOnError(emitter::completeWithError)
                .subscribe(token -> {
                    try {
                        emitter.send(SseEmitter.event().name("message").data(token));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });

        return emitter;
    }

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody Map<String, String> request) {
        Long adminId = BaseContext.getCurrentId();
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }
        try {
            String response = adminAiService.chat(adminId, message);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error("AI 对话失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/conversation")
    public Result<Void> clearConversation() {
        Long adminId = BaseContext.getCurrentId();
        adminAiService.clearConversation(adminId);
        return Result.success();
    }
}
