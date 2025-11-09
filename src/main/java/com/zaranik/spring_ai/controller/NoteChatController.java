package com.zaranik.spring_ai.controller;

import com.zaranik.spring_ai.mcp.service.ElicitationCoordinator;
import com.zaranik.spring_ai.message.repo.ChatMessageRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("note-chat")
class NoteChatController {

    private final ElicitationCoordinator coordinator;
    private final ChatMessageRepository chatMessageRepository;

    NoteChatController(ElicitationCoordinator coordinator, ChatMessageRepository chatMessageRepository) {
        this.coordinator = coordinator;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostMapping("confirm-creation")
    Mono<Void> confirmCreation(@RequestParam String chatId, @RequestParam boolean confirmed) {
        return Mono.fromRunnable(() -> coordinator.handleUserResponse(chatId, confirmed));
    }

    @PostMapping("clear/{chatId}")
    Mono<Void> clear(@PathVariable String chatId) {
        return chatMessageRepository.deleteAllByChatId(chatId);
    }

}
