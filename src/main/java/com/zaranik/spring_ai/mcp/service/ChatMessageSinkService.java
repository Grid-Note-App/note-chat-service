package com.zaranik.spring_ai.mcp.service;

import com.zaranik.spring_ai.message.dto.ChatMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatMessageSinkService {

    private final Map<String, Sinks.Many<ChatMessage>> sinks = new ConcurrentHashMap<>();

    public Sinks.Many<ChatMessage> getSink(String chatId) {
        return sinks.computeIfAbsent(chatId, id -> Sinks.many().multicast().onBackpressureBuffer());
    }

    public void emit(String chatId, ChatMessage message) {
        getSink(chatId).tryEmitNext(message);
    }

    public void complete(String chatId) {
        var sink = sinks.remove(chatId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
}