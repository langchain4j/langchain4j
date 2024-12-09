package dev.langchain4j.llama3;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ModelLoader {

    private static final String TOKENIZER_LLAMA_3_MODEL = "gpt2";

    private static final String LLAMA_3_PATTERN = "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+";

    private static Vocabulary loadVocabulary(Map<String, Object> metadata) {
        String model = (String) metadata.get("tokenizer.ggml.model");
        if (!TOKENIZER_LLAMA_3_MODEL.equals(model)) {
            throw new IllegalArgumentException("expected " + TOKENIZER_LLAMA_3_MODEL + " but found " + model);
        }
        String[] tokens = (String[]) metadata.get("tokenizer.ggml.tokens");
        return new Vocabulary(tokens, null);
    }

    public static  Llama loadModel(Path ggufPath, int contextLength, boolean loadWeights) throws IOException {
        GGUF gguf = GGUF.loadModel(ggufPath);
        FileChannel fileChannel = FileChannel.open(ggufPath, StandardOpenOption.READ);
        return loadModel(fileChannel, gguf, contextLength, loadWeights);
    }

    public static Llama loadModel(FileChannel fileChannel,  GGUF gguf, int contextLength, boolean loadWeights) throws IOException {
        try (var ignored = Timer.log("Load LlaMa model")) {
            Map<String, Object> metadata = gguf.getMetadata();
            Vocabulary vocabulary = loadVocabulary(metadata);
            Tokenizer tokenizer = createTokenizer(metadata, vocabulary);

            Llama.Configuration config = new Llama.Configuration(
                (int) metadata.get("llama.embedding_length"),
                (int) metadata.get("llama.feed_forward_length"),
                (int) metadata.get("llama.block_count"),
                (int) metadata.get("llama.attention.head_count"),

                metadata.containsKey("llama.attention.head_count_kv")
                    ? (int) metadata.get("llama.attention.head_count_kv")
                    : (int) metadata.get("llama.attention.head_count"),

                vocabulary.size(),
                (int) metadata.get("llama.context_length"),
                (float) metadata.getOrDefault("llama.attention.layer_norm_rms_epsilon", 1e-5f),
                (float) metadata.getOrDefault("llama.rope.freq_base", 10000f)
            ).withContextLength(contextLength);

            Llama.Weights weights = null;
            if (loadWeights) {
                Map<String, GGMLTensorEntry> tensorEntries = GGUF.loadTensors(fileChannel, gguf.getTensorDataOffset(), gguf.getTensorInfos());
                weights = loadWeights(tensorEntries, config);
            }
            return new Llama(config, tokenizer, weights);
        }
    }

    static Llama.Weights loadWeights(Map<String, GGMLTensorEntry> tensorEntries, Llama.Configuration config) {
        boolean ropeScaling = tensorEntries.containsKey("rope_freqs");
        float scaleFactor = 8;
        float loFreqFactor = 1;
        float hiFreqFactor = 3;
        int oldContextLength = 8192;
        Pair<float[], float[]> ropeFreqs = RoPE.precomputeFreqsCis(config.contextLength, config.headSize, config.ropeTheta,
            ropeScaling, scaleFactor, loFreqFactor, hiFreqFactor, oldContextLength);
        float[] ropeFreqsReal = ropeFreqs.first();
        float[] ropeFreqsImag = ropeFreqs.second();

        GGMLTensorEntry tokenEmbeddings = tensorEntries.get("token_embd.weight");
        Llama.Weights qw = new Llama.Weights(
            loadQuantized(tokenEmbeddings),
            loadArrayOfFloatBuffer(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_norm.weight")),
            loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_q.weight")),
            loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_k.weight")),
            loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_v.weight")),
            loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".attn_output.weight")),
            loadArrayOfFloatBuffer(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_norm.weight")),
            loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_gate.weight")), // w1
            loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_down.weight")), // w2
            loadArrayOfQuantized(config.numberOfLayers, i -> tensorEntries.get("blk." + i + ".ffn_up.weight")), // w3
            toFloatBuffer(tensorEntries.get("output_norm.weight")),
            FloatBuffer.wrap(ropeFreqsReal),
            FloatBuffer.wrap(ropeFreqsImag),
            // If "output.weight" is not present then the embedding weights are tied/shared with the decoder.
            // This is commonly referred as "tie word embeddings".
            loadQuantized(tensorEntries.getOrDefault("output.weight", tokenEmbeddings))
        );

        return qw;
    }

    private static Tokenizer createTokenizer(Map<String, Object> metadata, Vocabulary vocabulary) {
        String[] mergeLines = (String[]) metadata.get("tokenizer.ggml.merges");
        List<Pair<Integer, Integer>> merges = Arrays.stream(mergeLines)
            .map(line -> line.split(" "))
            .map(parts ->
                new Pair<>(
                    vocabulary.getIndex(parts[0]).orElseThrow(),
                    vocabulary.getIndex(parts[1]).orElseThrow())
            ).toList();

        int allTokens = vocabulary.size();
        int baseTokens = 128000; // assume all tokens after the base ones are special.
        int reservedSpecialTokens = allTokens - baseTokens;
        List<String> specialTokensList = Arrays.stream(vocabulary.tokens(), baseTokens, allTokens).toList();

        assert specialTokensList.stream().allMatch(token -> vocabulary.getIndex(token).isPresent());

        Map<String, Integer> specialTokens =
            IntStream.range(0, specialTokensList.size())
                .boxed()
                .collect(Collectors.toMap(
                    i -> specialTokensList.get(i),
                    i -> baseTokens + i)
                );

        return new Tokenizer(vocabulary, merges, LLAMA_3_PATTERN, specialTokens);
    }

    public static FloatTensor loadQuantized(GGMLTensorEntry entry) {
        GGMLType ggmlType = entry.ggmlType();
        return switch (ggmlType) {
            //case F32 -> new F32FloatTensor(FloatTensor.numberOfElements(entry.shape()), entry.memorySegment());
            case Q8_0 -> new Q8_0FloatTensor(FloatTensor.numberOfElements(entry.shape()), entry.memorySegment());
            case Q4_0 -> new Q4_0FloatTensor(FloatTensor.numberOfElements(entry.shape()), entry.memorySegment());
            default -> throw new UnsupportedOperationException("Quantization format " + ggmlType);
        };
    }

    public static FloatTensor[] loadArrayOfQuantized(int size, IntFunction<GGMLTensorEntry> getTensorEntry) {
        FloatTensor[] array = new FloatTensor[size];
        for (int i = 0; i < size; i++) {
            array[i] = loadQuantized(getTensorEntry.apply(i));
        }
        return array;
    }

    public static FloatBuffer[] loadArrayOfFloatBuffer(int size, IntFunction<GGMLTensorEntry> getTensorEntry) {
        FloatBuffer[] array = new FloatBuffer[size];
        for (int i = 0; i < size; i++) {
            array[i] = toFloatBuffer(getTensorEntry.apply(i));
        }
        return array;
    }

    public static FloatBuffer toFloatBuffer(GGMLTensorEntry tensorEntry) {
        GGMLType ggmlType = tensorEntry.ggmlType();
        return switch (ggmlType) {
            case F32 -> tensorEntry.memorySegment().asByteBuffer().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            default -> throw new UnsupportedOperationException("Conversion to " + ggmlType);
        };
    }
}
