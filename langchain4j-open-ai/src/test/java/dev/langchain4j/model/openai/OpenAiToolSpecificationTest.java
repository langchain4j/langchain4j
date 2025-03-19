package dev.langchain4j.model.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonTypeArraySchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

public class OpenAiToolSpecificationTest {

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(GPT_4_O_MINI)
            .strictJsonSchema(true)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_call_tool_with_array_parameter_with_multiple_allowed_types() throws JsonProcessingException {
        // create a tool whose parameter 'param1' is an array with multiple allowed types:
        // "param1" : {
        //   "type" : "array",
        //   "items" : {
        //     "type" : [ "boolean", "null", "integer" ]
        //   }
        // verify that the model can properly pass arguments to this tool
        JsonObjectSchema params = JsonObjectSchema.builder()
                .addProperty(
                        "param1",
                        JsonArraySchema.builder()
                                .items(JsonTypeArraySchema.builder().types(new String[]{"boolean", "null", "integer", "string"}).build())
                                .build())
                .required(List.of("param1"))
                .build();
        ToolSpecification toolSpecification = ToolSpecification.builder().name("function1").parameters(params).build();

        UserMessage userMessage = UserMessage.from(
                """
                Call function1 with param1=[3, null, 'abc',true].
                """);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecification)
                .build();

        ChatResponse chatResponse = model.chat(chatRequest);

        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest request = chatResponse.aiMessage().toolExecutionRequests().get(0);
        assertThat(request.arguments()).isEqualTo("{\"param1\":[3,null,\"abc\",true]}");
    }
}
