package com.zaranik.spring_ai.message.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatMessage(
    String id,
    ChatMessageType type,
    String content,
    NoteSuggestion noteSuggestion
) {

    public ChatMessage(String id, ChatMessageType type, String content) {
        this(id, type, content, null);
    }

    public record NoteSuggestion(
        String title,
        String description,
        String remindAt
    ) {

    }
}
