package dev.langchain4j.model.openai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiToolSpecificationIT {

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(GPT_4_O_MINI)
            .strictJsonSchema(true)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_call_tool_with_array_parameter_with_multiple_allowed_types() {
        // create a tool whose parameter 'param1' is an array with multiple allowed types:
        // "param1" : {
        //   "type" : "array",
        //   "items": {
        //     "anyOf": [
        //         {
        //             "type": "boolean"
        //         },
        //         {
        //             "type": "null"
        //         },
        //         {
        //             "type": "integer"
        //         }
        //     ]
        //    }
        //  }
        // verify that the model can properly pass arguments to this tool
        JsonAnyOfSchema arrayItems = JsonAnyOfSchema.builder()
                .anyOf(new JsonBooleanSchema(), new JsonNullSchema(), new JsonIntegerSchema())
                .build();
        JsonObjectSchema params = JsonObjectSchema.builder()
                .addProperty(
                        "param1", JsonArraySchema.builder().items(arrayItems).build())
                .required(List.of("param1"))
                .build();
        ToolSpecification toolSpecification =
                ToolSpecification.builder().name("function1").parameters(params).build();

        UserMessage userMessage = UserMessage.from(
                """
                        Call function1 with param1=[3, null, true].
                        """);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecification)
                .build();

        ChatResponse chatResponse = model.chat(chatRequest);

        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest request =
                chatResponse.aiMessage().toolExecutionRequests().get(0);
        assertThat(request.arguments()).isEqualToIgnoringWhitespace("{\"param1\":[3,null,true]}");
    }
}
