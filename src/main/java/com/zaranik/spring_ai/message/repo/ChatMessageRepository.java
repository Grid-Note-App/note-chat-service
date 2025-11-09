package com.zaranik.spring_ai.message.repo;

import com.zaranik.spring_ai.message.model.ChatMessageEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessageEntity, String> {

    Flux<ChatMessageEntity> findByChatIdOrderByCreatedAtAsc(String chatId);

    Mono<Void> deleteAllByChatId(String chatId);

}
