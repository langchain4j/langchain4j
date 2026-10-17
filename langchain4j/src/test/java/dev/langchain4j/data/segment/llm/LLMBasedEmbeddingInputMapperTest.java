package dev.langchain4j.data.segment.llm;

import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
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
}
