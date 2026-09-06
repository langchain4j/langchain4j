package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers how the non-streaming Responses API response body is read: which field ends up where in
 * the resulting {@link ChatResponse} metadata.
 */
class OpenAiResponsesChatModelWebSearchParsingTest {

    private static final String RESPONSE_BODY_WITH_WEB_SEARCH = """
            {
              "id": "resp_1",
              "model": "gpt-5.4-mini",
              "status": "completed",
              "output": [
                {
                  "type": "web_search_call",
                  "id": "ws_1",
                  "status": "completed",
                  "action": {"type": "search", "query": "langchain4j OpenAI Responses"}
                },
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "The answer cites sources.",
                      "annotations": [
                        {
                          "type": "url_citation",
                          "start_index": 4,
                          "end_index": 10,
                          "url": "https://docs.langchain4j.dev",
                          "title": "LangChain4j docs"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    private static final String RESPONSE_BODY_WITHOUT_WEB_SEARCH = """
            {
              "id": "resp_1",
              "model": "gpt-5.4-mini",
              "status": "completed",
              "output": [
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "content": [{"type": "output_text", "text": "Hello, world"}]
                }
              ]
            }
            """;

    private OpenAiResponsesChatModel modelWithResponseBody(String responseBody) {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(responseBody)
                .build());
        return OpenAiResponsesChatModel.builder()
                .apiKey("dummy")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.4-mini")
                .build();
    }

    @Test
    void should_expose_web_search_queries_and_citations_in_metadata() {
        ChatResponse response =
                modelWithResponseBody(RESPONSE_BODY_WITH_WEB_SEARCH).chat(List.of(UserMessage.from("Hello")));

        OpenAiResponsesChatResponseMetadata metadata = (OpenAiResponsesChatResponseMetadata) response.metadata();
        assertThat(metadata.webSearchMetadata()).isNotNull();
        assertThat(metadata.webSearchMetadata().searchQueries()).containsExactly("langchain4j OpenAI Responses");
        assertThat(metadata.webSearchMetadata().citations())
                .containsExactly(new OpenAiResponsesWebSearchMetadata.UrlCitation(
                        "https://docs.langchain4j.dev", "LangChain4j docs", 4, 10));
    }

    @Test
    void should_not_expose_web_search_metadata_when_output_has_no_search_data() {
        ChatResponse response =
                modelWithResponseBody(RESPONSE_BODY_WITHOUT_WEB_SEARCH).chat(List.of(UserMessage.from("Hello")));

        OpenAiResponsesChatResponseMetadata metadata = (OpenAiResponsesChatResponseMetadata) response.metadata();
        assertThat(metadata.webSearchMetadata()).isNull();
    }
}
