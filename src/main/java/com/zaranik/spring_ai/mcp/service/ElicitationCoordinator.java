package com.zaranik.spring_ai.mcp.service;

import com.zaranik.spring_ai.message.dto.ChatMessage;
import com.zaranik.spring_ai.message.dto.ChatMessageType;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ElicitationCoordinator {

    private final Map<String, Sinks.One<McpSchema.ElicitResult>> awaiting;
    private final ChatMessageSinkService sinkService;

    ElicitationCoordinator(ChatMessageSinkService sinkService) {
        this.sinkService = sinkService;
        this.awaiting = new ConcurrentHashMap<>();
    }

    public Mono<McpSchema.ElicitResult> awaitUserResponse(String chatId, String prompt, Map<String, Object> requestedSchema) {
        Sinks.One<McpSchema.ElicitResult> sink = Sinks.one();
        awaiting.put(chatId, sink);
        sendElicitationMessageToUser(chatId, prompt, requestedSchema);
        return sink.asMono().doFinally(_ -> awaiting.remove(chatId)).timeout(Duration.ofMinutes(2)).onErrorReturn(new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.CANCEL, Map.of("timeout", "Timeout of 2 minutes to respond has been reached")));
    }

    public void handleUserResponse(String chatId, boolean accepted) {
        Sinks.One<McpSchema.ElicitResult> sink = awaiting.remove(chatId);
        if (sink != null) {
            McpSchema.ElicitResult result = new McpSchema.ElicitResult(accepted ? McpSchema.ElicitResult.Action.ACCEPT : McpSchema.ElicitResult.Action.DECLINE, Map.of("confirmed", accepted));
            sink.tryEmitValue(result);
        } else {
            log.warn("No elicitation waiting for chat {}", chatId);
        }
    }

    private void sendElicitationMessageToUser(String chatId, String prompt, Map<String, Object> requestedSchema) {
        String title = (String) requestedSchema.get("title");
        String description = (String) requestedSchema.get("description");
        String remindAt = (String) requestedSchema.get("remindAt");
        ChatMessage.NoteSuggestion noteSuggestion = new ChatMessage.NoteSuggestion(title, description, remindAt);

        String chatMessageId = UUID.randomUUID().toString();
        ChatMessage chatMessage = ChatMessage.builder().id(chatMessageId).type(ChatMessageType.CONFIRM).content(prompt).noteSuggestion(noteSuggestion).build();
        sinkService.emit(chatId, chatMessage);
    }
}
