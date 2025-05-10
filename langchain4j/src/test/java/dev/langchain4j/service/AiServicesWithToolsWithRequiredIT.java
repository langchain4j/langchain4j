package dev.langchain4j.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AiServicesWithToolsWithRequiredIT {

    @Spy
    ChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Captor
    ArgumentCaptor<ChatRequest> chatRequestCaptor;

    interface Assistant {

        Result<String> chat(String userMessage);
    }

    /**
     * NOTE:
     * When used with the "tools" feature, all POJO fields and sub-fields are considered <b>required</b> by default.
     * This is different from "structured outputs" (see {@link AiServicesWithJsonSchemaWithRequiredIT}),
     * where all fields and sub-fields are considered <b>optional</b> by default.
     */
    @Test
    void should_execute_tool_with_pojo_with_optional_parameter() {

        // given
        class ToolWithPojoParameter {

            record Person(String name, @JsonProperty(required = false) Integer age) {
            }

            @Tool
            void process(Person person) {
                // this method is empty
            }

            static final JsonSchemaElement EXPECTED_SCHEMA = JsonObjectSchema.builder()
                    .addProperties(singletonMap("arg0", JsonObjectSchema.builder()
                            .addStringProperty("name")
                            .addIntegerProperty("age")
                            .required("name")
                            .build()))
                    .required("arg0")
                    .build();
        }

        ToolWithPojoParameter tool = spy(new ToolWithPojoParameter());

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tool)
                .build();

        String text = "Use 'process' tool to process the following: Klaus is 37 years old";

        // when
        assistant.chat(text);

        // then
        verify(tool).process(new ToolWithPojoParameter.Person("Klaus", 37));
        verifyNoMoreInteractions(tool);

        verify(model, times(2)).chat(chatRequestCaptor.capture());
        verifyNoMoreInteractionsFor(model);

        List<ToolSpecification> toolSpecifications = chatRequestCaptor.getValue().parameters().toolSpecifications();
        assertThat(toolSpecifications).hasSize(1);
        ToolSpecification toolSpecification = toolSpecifications.get(0);
        assertThat(toolSpecification.name()).isEqualTo("process");
        assertThat(toolSpecification.description()).isNull();
        assertThat(toolSpecification.parameters()).isEqualTo(ToolWithPojoParameter.EXPECTED_SCHEMA);
    }
}
