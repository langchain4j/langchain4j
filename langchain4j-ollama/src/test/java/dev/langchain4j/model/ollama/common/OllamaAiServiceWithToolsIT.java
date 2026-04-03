package dev.langchain4j.model.ollama.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.junit.jupiter.api.Disabled;

import java.util.List;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_1;

class OllamaAiServiceWithToolsIT extends AbstractOllamaToolsLanguageModelInfrastructure {

    ChatModel model = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName(LLAMA_3_1)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(model);
    }

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_execute_tool_with_pojo_with_primitives(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_execute_tool_with_pojo_with_nested_pojo(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_execute_tool_with_list_of_strings_parameter(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_execute_tool_with_list_of_POJOs_parameter(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void should_execute_tool_with_collection_of_integers_parameter(ChatModel model) {}

    @Override
    @Disabled("llama 3.1 cannot manage the invocation of 2 different tools in the same call")
    protected void should_return_immediately_from_first_tool_when_not_called_in_parallel(ChatModel model) { }

    @Override
    @Disabled("llama 3.1 cannot manage the invocation of 2 different tools in the same call")
    protected void should_return_to_LLM(ChatModel model) { }

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void  should_execute_immediate_tool_in_parallel_with_primitive_parameters(ChatModel model) { }

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void  should_execute_normal_tool_in_parallel_with_primitive_parameters(ChatModel model) { }

    @Override
    @Disabled("llama 3.1 cannot do it properly")
    protected void  should_execute_tool_with_enum_parameter(ChatModel model) { }
}
