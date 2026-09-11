package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParallelMapperEmptyInputTest {

    static final ChatModel DUMMY_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("test-response"))
                    .build();
        }
    };

    public interface ItemAgent {

        @UserMessage("Map: {{item}}")
        @Agent(description = "Maps a single item", outputKey = "mapped")
        String map(@V("item") String item);
    }

    public interface ListReturningMapperAgent {

        @Agent(outputKey = "mappedItems")
        List<String> mapAll(@V("items") List<String> items);
    }

    public interface ArrayReturningMapperAgent {

        @Agent
        String[] mapAll(@V("items") String... items);
    }

    @Test
    void empty_list_is_mapped_to_empty_list() {
        assertThat(listMapper().mapAll(List.of())).isNotNull().isEmpty();
    }

    @Test
    void non_empty_list_is_mapped_item_by_item() {
        assertThat(listMapper().mapAll(List.of("first", "second"))).containsExactly("test-response", "test-response");
    }

    @Test
    void empty_array_is_mapped_to_empty_array() {
        assertThat(arrayMapper().mapAll()).isNotNull().isEmpty();
    }

    @Test
    void non_empty_array_is_mapped_item_by_item() {
        assertThat(arrayMapper().mapAll("first", "second")).containsExactly("test-response", "test-response");
    }

    private static ListReturningMapperAgent listMapper() {
        return AgenticServices.parallelMapperBuilder(ListReturningMapperAgent.class)
                .subAgents(itemAgent())
                .build();
    }

    private static ArrayReturningMapperAgent arrayMapper() {
        return AgenticServices.parallelMapperBuilder(ArrayReturningMapperAgent.class)
                .subAgents(itemAgent())
                .build();
    }

    private static ItemAgent itemAgent() {
        return AgenticServices.agentBuilder(ItemAgent.class)
                .chatModel(DUMMY_MODEL)
                .outputKey("mapped")
                .build();
    }
}
