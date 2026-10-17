package dev.langchain4j.data.segment.llm;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.segment.EmbeddingInputMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * An {@link EmbeddingInputMapper} that uses a {@link ChatModel} to generate retrieval-oriented
 * descriptions for a {@link TextSegment}.
 * <p>
 * The LLM decides how many descriptions are useful. They are returned as a JSON array and converted
 * by {@link AiServices}. The original segment is always included as the first mapped segment so that
 * exact identifiers, class names, and phrases remain searchable.
 * <p>
 * Every call to {@link #map(TextSegment)} invokes the LLM. If the same content is ingested repeatedly,
 * cache the generated descriptions to avoid unnecessary calls.
 * <p>
 * {@link #mapAll(List)} maps segments concurrently using the configured {@link Executor}.
 *
 * @since 1.21.0
 */
@Experimental
public class LLMBasedEmbeddingInputMapper implements EmbeddingInputMapper {

    private final DescriptionGenerator descriptionGenerator;
    private final Executor executor;

    /**
     * Creates a mapper that uses the given chat model to generate embedding inputs.
     * <p>
     * Uses {@link ForkJoinPool#commonPool()} for concurrent {@link #mapAll(List)} calls.
     *
     * @param chatModel the chat model to use.
     */
    public LLMBasedEmbeddingInputMapper(ChatModel chatModel) {
        this(chatModel, ForkJoinPool.commonPool());
    }

    /**
     * Creates a mapper that uses the given chat model to generate embedding inputs.
     *
     * @param chatModel the chat model to use.
     * @param executor  the executor used to run {@link #mapAll(List)} calls concurrently.
     */
    public LLMBasedEmbeddingInputMapper(ChatModel chatModel, Executor executor) {
        ensureNotNull(chatModel, "chatModel");
        this.descriptionGenerator = AiServices.create(DescriptionGenerator.class, chatModel);
        this.executor = ensureNotNull(executor, "executor");
    }

    @Override
    public List<TextSegment> map(TextSegment segment) {
        ensureNotNull(segment, "segment");

        List<TextSegment> mapped = new ArrayList<>();
        mapped.add(TextSegment.from(segment.text(), segment.metadata()));

        List<String> descriptions = descriptionGenerator.describe(segment.text());
        if (descriptions != null) {
            for (String description : descriptions) {
                if (description != null && !description.isBlank()) {
                    mapped.add(TextSegment.from(description));
                }
            }
        }
        return mapped;
    }

    /**
     * Maps all the given segments concurrently.
     * <p>
     * The returned list preserves the order of the input segments. If one mapping fails, the remaining
     * tasks are cancelled and the failure is propagated.
     *
     * @param segments the original segments.
     * @return one list of embedding inputs per input segment, in the same order.
     */
    @Override
    public List<List<TextSegment>> mapAll(List<TextSegment> segments) {
        ensureNotNull(segments, "segments");
        if (segments.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<List<TextSegment>>> futures = new ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            futures.add(CompletableFuture.supplyAsync(() -> map(segment), executor));
        }

        List<List<TextSegment>> mappedSegments = new ArrayList<>(segments.size());
        try {
            for (CompletableFuture<List<TextSegment>> future : futures) {
                mappedSegments.add(join(future));
            }
        } catch (RuntimeException | Error e) {
            futures.forEach(future -> future.cancel(true));
            throw e;
        }
        return mappedSegments;
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    /**
     * The AI service used to generate retrieval-oriented descriptions.
     */
    interface DescriptionGenerator {
        @SystemMessage("""
            Generate concise, retrieval-oriented descriptions for the given text segment.
            The goal is to improve retrieval by representing the content in ways that
            match different user queries.

            For source code, consider descriptions combining code identifiers, purpose,
            functionality, parameters, return values, side effects, dependencies, and
            callers or callees when evident from the input.

            For ordinary text, consider multiple perspectives, such as key concepts,
            summaries, practical use cases, and hypothetical questions the content
            could answer.

            Apply similar strategies to other content types: identify their meaningful
            attributes and generate representations suited to likely search intents.

            Generate only distinct, useful descriptions grounded in the input.
            Do not invent facts or repeat the original text verbatim.

            Return only a valid JSON array of strings. Generate zero or more descriptions.
        """)
        List<String> describe(@UserMessage String text);
    }
}
