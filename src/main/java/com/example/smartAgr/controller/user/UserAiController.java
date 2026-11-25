package com.example.smartAgr.controller.user;

import com.example.smartAgr.context.BaseContext;
import com.example.smartAgr.result.Result;
import com.example.smartAgr.service.ai.UserAiService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/user/ai")
public class UserAiController {

    private final UserAiService userAiService;

    public UserAiController(UserAiService userAiService) {
        this.userAiService = userAiService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> request) {
        Long userId = BaseContext.getCurrentId();
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            SseEmitter errorEmitter = new SseEmitter();
            errorEmitter.completeWithError(new IllegalArgumentException("消息不能为空"));
            return errorEmitter;
        }

        SseEmitter emitter = new SseEmitter(300000L);

        userAiService.chatStream(userId, message)
                .doOnComplete(() -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    } catch (Exception ignored) {
                    }
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
        Long userId = BaseContext.getCurrentId();
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Result.error("消息不能为空");
        }
        try {
            String response = userAiService.chat(userId, message);
            return Result.success(response);
        } catch (Exception e) {
            return Result.error("AI 对话失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/conversation")
    public Result<Void> clearConversation() {
        Long userId = BaseContext.getCurrentId();
        userAiService.clearConversation(userId);
        return Result.success();
    }
}
