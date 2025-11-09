package com.zaranik.spring_ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaranik.spring_ai.mcp.service.ChatMessageSinkService;
import com.zaranik.spring_ai.message.dto.ChatMessage;
import com.zaranik.spring_ai.message.dto.ChatMessageType;
import com.zaranik.spring_ai.message.dto.InputMessageRequest;
import com.zaranik.spring_ai.message.model.ChatMessageEntity;
import com.zaranik.spring_ai.message.model.ChatMessageRole;
import com.zaranik.spring_ai.message.repo.ChatMessageRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageSinkService sinkService;

    public ChatWebSocketHandler(ChatClient.Builder clientBuilder,
                                ObjectMapper objectMapper,
                                ChatMessageRepository chatMessageRepository,
                                ToolCallbackProvider tools, ChatMessageSinkService sinkService) {
        this.chatClient = clientBuilder
            .defaultToolCallbacks(tools)
            .build();
        this.objectMapper = objectMapper;
        this.chatMessageRepository = chatMessageRepository;
        this.sinkService = sinkService;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String chatId = getChatId(session);
        String accessToken = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
            .build()
            .getQueryParams()
            .getFirst("token");
        log.info("Chat ID: {}, token = {}", chatId, accessToken);

        Flux<ChatMessage> chatMessageFlux = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .map(this::parseRequestMessage)
            .flatMap(request -> processChatRequest(chatId, request, accessToken));

        Flux<ChatMessage> outgoing = Flux.concat(getChatHistory(chatId), Flux.merge(chatMessageFlux, sinkService.getSink(chatId).asFlux()));

        return session.send(outgoing.map(this::serializeMessage).map(session::textMessage))
            .doFinally(_ -> sinkService.complete(chatId));
    }


    private Flux<ChatMessage> getChatHistory(String chatId) {
        return chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId)
            .map(entity -> {
                ChatMessageType type = switch (entity.getRole()) {
                    case USER -> ChatMessageType.USER;
                    case ASSISTANT -> ChatMessageType.ASSISTANT;
                };
                return ChatMessage.builder()
                    .id(entity.getId())
                    .content(entity.getContent())
                    .type(type)
                    .build();
            });
    }

    private Flux<ChatMessage> processChatRequest(String chatId, InputMessageRequest request, String accessToken) {
        StringBuffer fullResponse = new StringBuffer();
        Flux<ChatMessage> modelStream = Flux.defer(() -> chatClient.prompt(request.content())
                .toolContext(Map.of(
                    "accessToken", accessToken,
                    "chatId", chatId
                ))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)).stream().content())
            .map(messageToken -> new ChatMessage(UUID.randomUUID().toString(), ChatMessageType.TOKEN, messageToken))
            .doOnNext(msg -> fullResponse.append(msg.content()))
            .concatWithValues(new ChatMessage(UUID.randomUUID().toString(), ChatMessageType.END, null))
            .concatWith(Mono.defer(() -> {
                ChatMessageEntity entity = new ChatMessageEntity(chatId, ChatMessageRole.ASSISTANT, fullResponse.toString());
                return chatMessageRepository.save(entity)
                    .map(saved -> new ChatMessage(saved.getId(), ChatMessageType.ASSISTANT, saved.getContent()));
            }))
            .onErrorReturn(new ChatMessage(UUID.randomUUID().toString(), ChatMessageType.ERROR, "Streaming error"))
            .subscribeOn(Schedulers.boundedElastic());

        return chatMessageRepository.save(new ChatMessageEntity(chatId, ChatMessageRole.USER, request.content())).thenMany(modelStream);
    }

    private String getChatId(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("chatId");
    }

    @SneakyThrows
    private String serializeMessage(ChatMessage message) {
        return objectMapper.writeValueAsString(message);
    }

    @SneakyThrows
    private InputMessageRequest parseRequestMessage(String message) {
        return objectMapper.readValue(message, InputMessageRequest.class);
    }
}
