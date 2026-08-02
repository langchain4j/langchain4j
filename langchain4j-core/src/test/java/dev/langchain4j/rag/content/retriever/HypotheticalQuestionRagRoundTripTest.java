package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.ORIGINAL_TEXT_METADATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.IngestionResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class HypotheticalQuestionRagRoundTripTest {

    static class TestEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = textSegments.stream()
                    .map(textSegment ->
                            Embedding.from(List.of((float) textSegment.text().length())))
                    .toList();
            return Response.from(embeddings, new TokenUsage(textSegments.size()));
        }
    }

    static class TestEmbeddingStore implements EmbeddingStore<TextSegment> {

        private final List<String> ids = new ArrayList<>();
        private final List<Embedding> embeddings = new ArrayList<>();
        private final List<TextSegment> segments = new ArrayList<>();

        @Override
        public String add(Embedding embedding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(String id, Embedding embedding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String add(Embedding embedding, TextSegment embedded) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
            this.ids.addAll(ids);
            this.embeddings.addAll(embeddings);
            this.segments.addAll(embedded);
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                double score = segment.text().equals(request.query()) ? 1.0 : 0.1;
                if (score >= request.minScore()) {
                    matches.add(new EmbeddingMatch<>(score, ids.get(i), embeddings.get(i), segment));
                }
            }
            matches.sort(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score)
                    .reversed());
            if (matches.size() > request.maxResults()) {
                matches = new ArrayList<>(matches.subList(0, request.maxResults()));
            }
            return new EmbeddingSearchResult<>(matches);
        }

        List<TextSegment> segments() {
            return segments;
        }
    }

    @Test
    void should_round_trip_original_text_through_hqe_ingestion_and_retrieval() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                dev.langchain4j.data.message.AiMessage.from("What is photosynthesis?\nHow do plants make food?"),
                dev.langchain4j.data.message.AiMessage.from("What is mitosis?\nHow do cells divide?"));

        HypotheticalQuestionTextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
                .chatModel(chatModel)
                .numberOfQuestions(2)
                .build();

        TestEmbeddingModel embeddingModel = new TestEmbeddingModel();
        TestEmbeddingStore embeddingStore = new TestEmbeddingStore();

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .textSegmentTransformer(transformer)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        IngestionResult ingestionResult = ingestor.ingest(List.of(
                Document.from("Plants convert sunlight into energy.", Metadata.from("source", "plants")),
                Document.from("Mitosis divides one cell into two cells.", Metadata.from("source", "cells"))));

        assertThat(ingestionResult.tokenUsage()).isEqualTo(new TokenUsage(4));
        assertThat(embeddingStore.segments()).hasSize(4);
        assertThat(embeddingStore.segments())
                .allSatisfy(segment -> assertThat(segment.metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                        .isNotNull());

        ContentRetriever retriever = HypotheticalQuestionContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(1)
                .candidateMaxResults(4)
                .build();

        List<Content> contents = retriever.retrieve(Query.from("What is photosynthesis?"));

        assertThat(contents).singleElement().satisfies(content -> {
            assertThat(content.textSegment().text()).isEqualTo("Plants convert sunlight into energy.");
            assertThat(content.textSegment().metadata().getString("source")).isEqualTo("plants");
            assertThat(content.textSegment().metadata().getString(ORIGINAL_TEXT_METADATA_KEY))
                    .isNull();
        });
    }
}
