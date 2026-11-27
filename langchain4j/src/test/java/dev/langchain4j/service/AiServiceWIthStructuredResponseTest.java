package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AiServiceWIthStructuredResponseTest {

    public enum RequestCategory {
        LEGAL,
        MEDICAL,
        TECHNICAL,
        UNKNOWN
    }

    public record RequestClassifierResponse(RequestCategory category) {}

    public interface CategoryRouter {
        RequestClassifierResponse classify(@UserMessage String request);
    }

    static class LowerCaseEnumChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"category\" : \"legal\"}"))
                    .build();
        }
    }

    @Test
    void caseInsesitiveEnum() {
        CategoryRouter categoryRouter = AiServices.builder(CategoryRouter.class)
                .chatModel(new LowerCaseEnumChatModel())
                .build();

        RequestClassifierResponse response = categoryRouter.classify("Some request");
        assertThat(response.category()).isEqualTo(RequestCategory.LEGAL);
    }

    public record Person(String name, int age) {}

    public interface PersonExtractor {
        List<Person> extract(@UserMessage String text);
    }

    @Test
    void list_of_pojo_with_model_that_does_not_support_json_schema() {
        // given
        MockChatModel model = MockChatModel.thatResponds(
                "{\"values\": [{\"name\": \"Alice\", \"age\": 30}, {\"name\": \"Bob\", \"age\": 25}]}");

        PersonExtractor extractor =
                AiServices.builder(PersonExtractor.class).chatModel(model).build();

        // when
        List<Person> people = extractor.extract("Alice 30, Bob 25");

        // then
        assertThat(people).containsExactly(new Person("Alice", 30), new Person("Bob", 25));

        ChatRequest request = model.request();
        assertThat(request.responseFormat()).isNull();
        assertThat(((dev.langchain4j.data.message.UserMessage)
                                request.messages().get(0))
                        .singleText())
                .startsWith("Alice 30, Bob 25")
                .contains("You must answer strictly in the following JSON format: {\n\"values\": (type: array of ")
                .contains("\"name\": (type: string)")
                .contains("\"age\": (type: integer)");
    }
}
