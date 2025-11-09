package com.zaranik.spring_ai.message.dto;

public enum ChatMessageType {
    // user prompts
    USER,
    // assistant final content message
    ASSISTANT,

    // assistant partial content
    TOKEN,
    CONFIRM,
    END,
    ERROR,
}
