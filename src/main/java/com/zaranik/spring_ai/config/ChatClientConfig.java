package com.zaranik.spring_ai.config;

import com.zaranik.spring_ai.mcp.service.ElicitationCoordinator;
import io.modelcontextprotocol.client.McpClient;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.customizer.McpAsyncClientCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Configuration
public class ChatClientConfig {

    @Bean
    ChatClientCustomizer chatClientCustomizer(ChatMemory chatMemory) {
        return chatClientBuilder -> {
            chatClientBuilder
                .defaultSystem("""
                
                answer in that question that prompt is written
                AGAIN: answer in that question that prompt is written
                You must always use the currentDateTime tool when reasoning about current or future times.
                Never ask your whether to call currentDateTime tool, just call it when you need.
                Do not reuse or assume previous time values.
                
                You must always use the currentDateTime before using calculateFutureTime tool when reasoning about current or future times.
                Do not reuse or assume previous time values.
                
                AGAIN: Do not reuse or assume previous time values.
                
                You must always use currentDateTime and calculateFutureTime tools before calling the createNote tool (it's remindAt param)
                
                use tool getAllNotes to provide user list of all his notes
                AGAIN: just call tool getAllNotes to provide user list of all his notes
                
                """)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        };
    }

    @Component
    static class CustomMcpAsyncClientCustomizer implements McpAsyncClientCustomizer {

        @Autowired
        ElicitationCoordinator elicitationCoordinator;

        @Override
        public void customize(String name, McpClient.AsyncSpec spec) {
            spec
                .requestTimeout(Duration.ofSeconds(30))
                .elicitation(elicitRequest -> {
                    String chatId = elicitRequest.meta().get("chatId").toString();
                    return elicitationCoordinator.awaitUserResponse(chatId, elicitRequest.message(), elicitRequest.requestedSchema());
                });
        }
    }

}
