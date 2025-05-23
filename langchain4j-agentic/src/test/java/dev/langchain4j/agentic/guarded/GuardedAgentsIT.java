package dev.langchain4j.agentic.guarded;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.ChatState;
import dev.langchain4j.agentic.DefaultChatState;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollama;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class GuardedAgentsIT {
    private static final String OLLAMA_BASE_URL = ollamaBaseUrl(ollama);

    static final ChatModel model = OllamaChatModel.builder()
            .baseUrl(OLLAMA_BASE_URL)
            .modelName("qwen2.5:7b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    public interface RequestClassifierAgent {

        @UserMessage("""
            Categorize the user request returning only one word among 'legal', 'medical' or 'technical',
            and nothing else, avoiding any explanation.
            
            The user request is: '{{it}}'.
            """)
        @Agent("An agent that categorizes the user request")
        String categorizeRequest(String request);
    }

    public interface MedicalExpert {

        @UserMessage("""
            You are a medical expert.
            Analyze the following user request under a medical point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A medical expert")
        @Agent("A medical expert")
        String medical(String request);
    }

    public interface LegalExpert {

        @UserMessage("""
            You are a legal expert.
            Analyze the following user request under a legal point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A legal expert")
        @Agent("A legal expert")
        String legal(String request);
    }

    public interface TechnicalExpert {

        @UserMessage("""
            You are a technical expert.
            Analyze the following user request under a technical point of view and provide the best possible answer.
            The user request is {{it}}.
            """)
        @Tool("A technical expert")
        @Agent("A technical expert")
        String technical(String request);
    }

    @Test
    void guarded_agents_tests() {
        // All user's interactions go through the router agent, but it now can redirect the request
        // to the appropriate expert and keep track of this decision in the conversation state
        // for subsequent requests.

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // ChatState enriches the chat memory with additional state information, just a Map
        ChatState chatState = new DefaultChatState(chatMemory);

        MedicalExpert medicalExpert = spy(GuardedAgentServices.builder(MedicalExpert.class)
                .chatModel(model)
                .chatState(chatState)
                .build());
        LegalExpert legalExpert = spy(GuardedAgentServices.builder(LegalExpert.class)
                .chatModel(model)
                .chatState(chatState)
                .build());
        TechnicalExpert technicalExpert = spy(GuardedAgentServices.builder(TechnicalExpert.class)
                .chatModel(model)
                .chatState(chatState)
                .build());

        AtomicInteger routerInvocationCounter = new AtomicInteger(0);
        AtomicInteger expertInvocationCounter = new AtomicInteger(0);

        RequestClassifierAgent routerAgent = GuardedAgentServices.builder(RequestClassifierAgent.class)
                .chatModel(model)
                .chatState(chatState)
                .onRequest( request -> {
                    if (request.chatState().hasState("expertType")) {
                        expertInvocationCounter.incrementAndGet();
                        String agentName = request.chatState().readState("expertType").toString();
                        return AgentDirective.redirectTo(agentName);
                    }
                    return AgentDirective.prompt();
                })
                .onResponse( response -> {
                    if (response.agentName().equals("categorizeRequest")) {
                        routerInvocationCounter.incrementAndGet();
                        response.chatState().writeState("expertType", response.response());
                        return AgentDirective.prompt();
                    }
                    return AgentDirective.terminate();
                })
                .agents(medicalExpert, legalExpert, technicalExpert)
                .build();

        String response1 = routerAgent.categorizeRequest("I broke my leg what should I do");
        System.out.println(response1);
        verify(medicalExpert).medical("I broke my leg what should I do");

        // Both the router and the expert are invoked once
        assertThat(routerInvocationCounter.get()).isEqualTo(1);
        assertThat(expertInvocationCounter.get()).isEqualTo(1);

        String response2 = routerAgent.categorizeRequest("Which part of my body is broken?");
        System.out.println(response2);
        assertThat(response2).containsIgnoringCase("leg");

        // The request is redirected to expert so only that one is invoked
        assertThat(routerInvocationCounter.get()).isEqualTo(1);
        assertThat(expertInvocationCounter.get()).isEqualTo(2);
    }
}
