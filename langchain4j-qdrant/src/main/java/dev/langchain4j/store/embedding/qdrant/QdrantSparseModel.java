package dev.langchain4j.store.embedding.qdrant;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import io.qdrant.client.grpc.Points;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A sparse model executed server-side by Qdrant (built-in or cloud inference).
 *
 * <p>Unlike a {@link SparseEncoder}, the sparse vector is never computed on the client:
 * the raw text is sent to Qdrant as a {@code Document} and the server converts it
 * into a sparse vector using the configured model.
 *
 * <p>For the built-in {@code qdrant/bm25} model the sparse vector must be configured with
 * {@code Modifier.Idf} when the collection is created, otherwise the IDF part of the BM25
 * score is missing.
 */
public final class QdrantSparseModel {

    /** Name of Qdrant's built-in BM25 model. */
    public static final String BM25_MODEL = "qdrant/bm25";

    private final String model;
    private final Map<String, Object> options;

    private QdrantSparseModel(String model, Map<String, Object> options) {
        this.model = ensureNotBlank(model, "model");
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(ensureNotNull(options, "options")));
    }

    /**
     * @return Qdrant's built-in BM25 model with default options.
     */
    public static QdrantSparseModel bm25() {
        return bm25Builder().build();
    }

    /**
     * @return A builder for Qdrant's built-in BM25 model.
     */
    public static Bm25Builder bm25Builder() {
        return new Bm25Builder();
    }

    /**
     * @param model   The name of a sparse model supported by the Qdrant instance.
     * @param options Model-specific options, sent as-is to Qdrant.
     */
    public static QdrantSparseModel of(String model, Map<String, Object> options) {
        return new QdrantSparseModel(model, options);
    }

    /**
     * @param model The name of a sparse model supported by the Qdrant instance.
     */
    public static QdrantSparseModel of(String model) {
        return new QdrantSparseModel(model, Map.of());
    }

    public String model() {
        return model;
    }

    public Map<String, Object> options() {
        return options;
    }

    Points.Document toDocument(String text) {
        return Points.Document.newBuilder()
                .setText(text)
                .setModel(model)
                .putAllOptions(ValueMapFactory.valueMap(options))
                .build();
    }

    /**
     * Builder for Qdrant's built-in BM25 model. Options left unset use Qdrant's defaults.
     */
    public static class Bm25Builder {

        private final Map<String, Object> options = new LinkedHashMap<>();

        /**
         * @param language Language used for stemming and stopwords (e.g. "english", "german").
         */
        public Bm25Builder language(String language) {
            return option("language", language);
        }

        /**
         * @param k Term frequency saturation parameter. Qdrant default: 1.2.
         */
        public Bm25Builder k(double k) {
            return option("k", k);
        }

        /**
         * @param b Document length normalization factor. Qdrant default: 0.75.
         */
        public Bm25Builder b(double b) {
            return option("b", b);
        }

        /**
         * @param avgLen Expected average document length in tokens. Qdrant default: 256.
         */
        public Bm25Builder avgLen(double avgLen) {
            return option("avg_len", avgLen);
        }

        /**
         * @param lowercase Whether tokens are lowercased. Qdrant default: true.
         */
        public Bm25Builder lowercase(boolean lowercase) {
            return option("lowercase", lowercase);
        }

        /**
         * @param asciiFolding Whether accented characters are normalized to ASCII. Qdrant default: false.
         */
        public Bm25Builder asciiFolding(boolean asciiFolding) {
            return option("ascii_folding", asciiFolding);
        }

        /**
         * @param tokenizer One of "word" (Qdrant default), "whitespace", "prefix" or "multilingual".
         */
        public Bm25Builder tokenizer(String tokenizer) {
            return option("tokenizer", tokenizer);
        }

        /**
         * @param minTokenLen Minimum token length to include.
         */
        public Bm25Builder minTokenLen(int minTokenLen) {
            return option("min_token_len", minTokenLen);
        }

        /**
         * @param maxTokenLen Maximum token length to include.
         */
        public Bm25Builder maxTokenLen(int maxTokenLen) {
            return option("max_token_len", maxTokenLen);
        }

        /**
         * Sets an arbitrary BM25 option (e.g. "stemmer" or "stopwords"), sent as-is to Qdrant.
         */
        public Bm25Builder option(String key, Object value) {
            options.put(ensureNotBlank(key, "key"), ensureNotNull(value, key));
            return this;
        }

        public QdrantSparseModel build() {
            return new QdrantSparseModel(BM25_MODEL, options);
        }
    }
}
