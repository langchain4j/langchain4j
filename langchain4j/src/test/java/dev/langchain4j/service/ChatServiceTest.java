package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.ToolExecutionStrategy.RETURN_TOOL_EXECUTION_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {

    ChatLanguageModel chatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .responseFormat("json_schema")
            .strictJsonSchema(true)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void test_simple_chat() {

        // given
        ChatService chatService = ChatService.createFrom(chatModel);

        // when
        String answer = chatService.chat("What is the capital of Germany?");

        // then
        assertThat(answer).contains("Berlin");
    }

    @Test
    void test_system_message() {

        // given
        ChatService chatService = ChatService.builder()
                .chatModel(chatModel)
                .systemMessage("Answer in English")
                .build();

        // when
        String answer = chatService.buildRequest()
                .systemMessage("Answer in German")
                .userMessage("Translate 'I love you'")
                .outputString();

        // then
        assertThat(answer).contains("liebe");
    }

    @Test
    void test_template_variables() {

        // given
        ChatService chatService = ChatService.builder()
                .chatModel(chatModel)
                .systemMessage("You are a {{type}} assistant")
                .userMessage("Tell me about {{thing}}")
                .build();

        // when
        String answer = chatService.buildRequest()
                .variable("type", "helpful")
                .variable("thing", "AI")
                .outputString();

        // then
        assertThat(answer).containsIgnoringCase("AI");
    }

    @Test
    void test_parameters() {

        // given
        ChatService chatService = ChatService.builder()
                .chatModel(chatModel)
                .temperature(0.0)
                .build();

        // when
        String answer = chatService.buildRequest()
                .systemMessage("What is the capital of Germany?")
                .temperature(0.7)
                .outputString();

        // then
        assertThat(answer).contains("Berlin");
    }

    @Test
    void test_structured_output() {

        // given
        record Person(String name, int age) {
        }

        ChatService chatService = ChatService.createFrom(chatModel);

        // when
        Person person = chatService.buildRequest()
                .userMessage("Klaus is 42 years old")
                .output(Person.class);

        // then
        assertThat(person).isEqualTo(new Person("Klaus", 42));
    }

    @Test
    @Disabled
    void test_structured_output_list() {

        // given
        record Person(String name, int age) {
        }

        ChatService chatService = ChatService.createFrom(chatModel);

        // when
        List<Person> people = chatService.buildRequest()
                .userMessage("Klaus is 42 years old, Francine is 45 years old")
                .outputListOf(Person.class);

        // then
        assertThat(people).containsExactly(
                new Person("Klaus", 42),
                new Person("Francine", 45)
        );
    }

    @Test
    void test_memory() {

        // given
        ChatService chatService = ChatService.builder()
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        chatService.chat("My name is Klaus");

        // when
        String answer = chatService.chat("What is my name?");

        // then
        assertThat(answer).contains("Klaus");
    }

    @Test
    void test_tools() {

        // given
        ChatService chatService = ChatService.builder()
                .chatModel(chatModel)
                .tools(new Tools())
                .build();

        // when
        String answer = chatService.chat("Is it cold?");

        // then
        assertThat(answer).contains("17");
    }

    @Test
    void test_selected_tools() {

        // given
        ChatService chatService = ChatService.builder()
                .chatModel(chatModel)
                .tools(new Tools())
                .build();

        // when
        String answer = chatService.buildRequest()
                .userMessage("Is it cold?")
                .toolNames("getCurrentTemperature")
                .outputString();

        // then
        assertThat(answer).contains("17");
    }

    @Test
    void test_tools_return_execution_request() {

        // given
        ChatService chatService = ChatService.builder()
                .chatModel(chatModel)
                .tools(new Tools())
                .onToolCall(RETURN_TOOL_EXECUTION_REQUEST)
                .build();

        // when
        ChatResponse chatResponse = chatService.buildRequest()
                .userMessage("Is it cold?")
                .outputResponse();

        // then
        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(1);
    }

    static class Tools {

        @Tool
        LocalTime getCurrentTime() {
            return LocalTime.now();
        }

        @Tool
        int getCurrentTemperature() {
            return 17;
        }
    }
}
