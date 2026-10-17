package dev.langchain4j.data.segment.llm;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LLMBasedEmbeddingInputMapperTest {

    @Test
    void should_map_segment_to_original_and_llm_descriptions() {

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder().aiMessage(AiMessage.from("""
                                [
                                  "Parses CSV files into rows.",
                                  "Use when importing tabular data.",
                                  "How do I parse a CSV file?"
                                ]
                                """)).build());

        LLMBasedEmbeddingInputMapper mapper = new LLMBasedEmbeddingInputMapper(chatModel);

        List<TextSegment> mapped = mapper.map(textSegment("class CsvParser {}"));

        assertThat(mapped).hasSize(4);
        assertThat(mapped.get(0).text()).isEqualTo("class CsvParser {}");
        assertThat(mapped.get(1).text()).isEqualTo("Parses CSV files into rows.");
        assertThat(mapped.get(2).text()).isEqualTo("Use when importing tabular data.");
        assertThat(mapped.get(3).text()).isEqualTo("How do I parse a CSV file?");
    }

    @Test
    void should_keep_only_original_segment_when_llm_returns_empty_list() {

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder().aiMessage(AiMessage.from("[]")).build());

        LLMBasedEmbeddingInputMapper mapper = new LLMBasedEmbeddingInputMapper(chatModel);

        List<TextSegment> mapped = mapper.map(textSegment("class CsvParser {}"));

        assertThat(mapped).containsExactly(textSegment("class CsvParser {}"));
    }

    @Test
    void should_filter_out_blank_descriptions() {

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("[\"\", \"  \", \"Parses CSV files.\"]"))
                        .build());

        LLMBasedEmbeddingInputMapper mapper = new LLMBasedEmbeddingInputMapper(chatModel);

        List<TextSegment> mapped = mapper.map(textSegment("class CsvParser {}"));

        assertThat(mapped).containsExactly(textSegment("class CsvParser {}"), textSegment("Parses CSV files."));
    }

    @Test
    void should_reject_null_chat_model() {

        assertThatThrownBy(() -> new LLMBasedEmbeddingInputMapper(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatModel");
    }

    @Test
    void should_preserve_segment_order_in_map_all() {

        ChatModel chatModel = mock(ChatModel.class);
        AtomicInteger callCounter = new AtomicInteger();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenAnswer(invocation -> ChatResponse.builder()
                        .aiMessage(AiMessage.from("[\"description " + callCounter.incrementAndGet() + "\"]"))
                        .build());

        LLMBasedEmbeddingInputMapper mapper = new LLMBasedEmbeddingInputMapper(chatModel, Runnable::run);

        List<List<TextSegment>> mapped = mapper.mapAll(List.of(textSegment("first"), textSegment("second")));

        assertThat(mapped).hasSize(2);
        assertThat(mapped.get(0).get(1).text()).isEqualTo("description 1");
        assertThat(mapped.get(1).get(1).text()).isEqualTo("description 2");
    }

    @Test
    void should_map_all_concurrently() throws Exception {

        ChatModel chatModel = mock(ChatModel.class);
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            barrier.await(5, TimeUnit.SECONDS);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("[\"description\"]"))
                    .build();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            LLMBasedEmbeddingInputMapper mapper = new LLMBasedEmbeddingInputMapper(chatModel, executor);

            List<List<TextSegment>> mapped = mapper.mapAll(List.of(textSegment("first"), textSegment("second")));

            assertThat(mapped).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_return_empty_list_for_empty_map_all() {

        ChatModel chatModel = mock(ChatModel.class);
        LLMBasedEmbeddingInputMapper mapper = new LLMBasedEmbeddingInputMapper(chatModel);

        assertThat(mapper.mapAll(List.of())).isEmpty();
        verifyNoInteractions(chatModel);
    }
}
