package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import dev.langchain4j.service.AiServices;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxAIServiceIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    record Result(Double result) {}
    ;

    static class Calculator {
        @Tool("Adds two given numbers")
        double add(double a, double b) {
            return a + b;
        }

        @Tool("Multiplies two given numbers")
        String multiply(double a, double b) {
            return String.valueOf(a * b);
        }
    }

    interface Assistant {
        Result chat(String userMessage);
    }

    @Test
    public void should_not_loop_when_tool_choice_required() {

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(WatsonxChatModel.builder()
                        .url(URL)
                        .apiKey(API_KEY)
                        .projectId(PROJECT_ID)
                        .modelName("ibm/granite-4-h-small")
                        .logRequests(true)
                        .logResponses(true)
                        .timeLimit(Duration.ofSeconds(30))
                        .toolChoice(ToolChoice.REQUIRED)
                        .temperature(0.5)
                        .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                        .build())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(new Calculator())
                .build();

        var result = assistant.chat("What is the result of 2 + 2?");
        assertEquals(4, result.result());
    }
}
