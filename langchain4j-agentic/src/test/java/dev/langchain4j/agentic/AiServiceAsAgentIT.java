package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.planner.AgentsSystem;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollama;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class AiServiceAsAgentIT {

    private static final ChatModel model = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName("qwen2.5:7b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    public interface RouterAgent {

        @UserMessage("""
            Analyze the following user request and categorize it as 'legal', 'medical' or 'technical',
            then forward the request as it is to the corresponding expert provided as a tool.
            Finally return the answer that you received from the expert without any modification.

            The user request is: '{{it}}'.
            """)
        String askToExpert(String request);
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
    void tools_as_agents_tests() {
        // Nothing conceptually new, just test that now we can use AiServices as tools

        MedicalExpert medicalExpert = spy(AiServices.builder(MedicalExpert.class)
                .chatModel(model)
                .build());
        LegalExpert legalExpert = spy(AiServices.builder(LegalExpert.class)
                .chatModel(model)
                .build());
        TechnicalExpert technicalExpert = spy(AiServices.builder(TechnicalExpert.class)
                .chatModel(model)
                .build());

        RouterAgent routerAgent = AiServices.builder(RouterAgent.class)
                .chatModel(model)
                .tools(medicalExpert, legalExpert, technicalExpert)
                .build();

        System.out.println(routerAgent.askToExpert("I broke my leg what should I do"));

        verify(medicalExpert).medical("I broke my leg what should I do");
    }

    public interface ExpertsAggregatorAgent {

        @UserMessage("""
            Categorize the user request returning only one word among 'legal', 'medical' or 'technical',
            and nothing else, avoiding any explanation.
            
            The user request is: '{{it}}'.
            """)
        @Agent("An agent that categorizes the user request")
        String askToExpert(String request);
    }

    @Test
    void agentic_tests() {
        // All user's interactions go through the router agent, but it now can redirect the request
        // to the appropriate expert and keep track of this decision in the conversation state
        // for subsequent requests.

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // ChatState enriches the chat memory with additional state information, just a Map
        ChatState chatState = new DefaultChatState(chatMemory);

        MedicalExpert medicalExpert = spy(AgentServices.builder(MedicalExpert.class)
                .chatModel(model)
                .chatState(chatState)
                .build());
        LegalExpert legalExpert = spy(AgentServices.builder(LegalExpert.class)
                .chatModel(model)
                .chatState(chatState)
                .build());
        TechnicalExpert technicalExpert = spy(AgentServices.builder(TechnicalExpert.class)
                .chatModel(model)
                .chatState(chatState)
                .build());

        AtomicInteger routerInvocationCounter = new AtomicInteger(0);
        AtomicInteger expertInvocationCounter = new AtomicInteger(0);

        ExpertsAggregatorAgent routerAgent = AgentServices.builder(ExpertsAggregatorAgent.class)
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
                    if (response.agentName().equals("askToExpert")) {
                        routerInvocationCounter.incrementAndGet();
                        response.chatState().writeState("expertType", response.response());
                        return AgentDirective.prompt();
                    }
                    return AgentDirective.terminate();
                })
                .agents(medicalExpert, legalExpert, technicalExpert)
                .build();

        String response1 = routerAgent.askToExpert("I broke my leg what should I do");
        System.out.println(response1);
        verify(medicalExpert).medical("I broke my leg what should I do");

        // Both the router and the expert are invoked once
        assertThat(routerInvocationCounter.get()).isEqualTo(1);
        assertThat(expertInvocationCounter.get()).isEqualTo(1);

        String response2 = routerAgent.askToExpert("Which part of my body is broken?");
        System.out.println(response2);
        assertThat(response2).containsIgnoringCase("leg");

        // The request is redirected to expert so only that one is invoked
        assertThat(routerInvocationCounter.get()).isEqualTo(1);
        assertThat(expertInvocationCounter.get()).isEqualTo(2);
    }

    private static final ChatModel plannerModel = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName("qwen3:8b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void agents_system_test() {
        // All agents are registered in the AgentsSystem, which internally uses a planner agent that can invoke other agents

        ExpertsAggregatorAgent expertsAggregatorAgent = AgentServices.builder(ExpertsAggregatorAgent.class)
                .chatModel(model)
                .build();
        MedicalExpert medicalExpert = AgentServices.builder(MedicalExpert.class)
                .chatModel(model)
                .build();
        LegalExpert legalExpert = AgentServices.builder(LegalExpert.class)
                .chatModel(model)
                .build();
        TechnicalExpert technicalExpert = AgentServices.builder(TechnicalExpert.class)
                .chatModel(model)
                .build();

        AgentsSystem agentsSystem = new AgentsSystem(plannerModel, expertsAggregatorAgent, medicalExpert, legalExpert, technicalExpert);
        System.out.println(agentsSystem.execute("I broke my leg what should I do"));
    }
}
