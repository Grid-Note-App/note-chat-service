package com.zaranik.spring_ai.message.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("chat_messages")
public class ChatMessageEntity implements Persistable<String> {

    @Id
    private String id;

    private String chatId;
    private ChatMessageRole role;
    private String content;

    @CreatedDate
    private LocalDateTime createdAt;

    @PersistenceCreator
    public ChatMessageEntity(String chatId, ChatMessageRole role, String content) {
        this.chatId = chatId;
        this.role = role;
        this.content = content;
    }

    @Override
    public boolean isNew() {
        return id == null;
    }

}