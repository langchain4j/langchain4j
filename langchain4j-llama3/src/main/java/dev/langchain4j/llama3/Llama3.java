package dev.langchain4j.llama3;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Llama3 {

    private static final Logger LOG = LoggerFactory.getLogger(Llama3.class);

    public static Sampler selectSampler(int vocabularySize, float temperature, float topp, long rngSeed) {
        LOG.debug("Creating sampler with temperature={}, topp={}, seed={}", temperature, topp, rngSeed);

        Sampler sampler;
        if (temperature == 0.0f) {
            // greedy argmax sampling: take the token with the highest probability
            sampler = Sampler.ARGMAX;
        } else {
            // we sample from this distribution to get the next token
            RandomGenerator rng = RandomGeneratorFactory.getDefault().create(rngSeed);
            Sampler innerSampler;
            if (topp <= 0 || topp >= 1) {
                // simply sample from the predicted probability distribution
                innerSampler = new CategoricalSampler(rng);
            } else {
                // top-p (nucleus) sampling, clamping the least likely tokens to zero
                innerSampler = new ToppSampler(vocabularySize, topp, rng);
            }
            sampler = logits -> {
                // apply the temperature to the logits
                logits.divideInPlace(0, logits.size(), temperature);
                // apply softmax to the logits to get the probabilities for next token
                logits.softmaxInPlace(0, logits.size());
                return innerSampler.sampleToken(logits);
            };
        }
        return sampler;
    }

    public static void runInteractive( Llama model, Sampler sampler,  Options options) {
        Llama.State state = null;
        List<Integer> conversationTokens = new ArrayList<>();
        ChatFormat chatFormat = new ChatFormat(model.tokenizer());
        conversationTokens.add(chatFormat.beginOfText);
        if (options.systemPrompt() != null) {
            conversationTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, options.systemPrompt())));
        }
        int startPosition = 0;
        Scanner in = new Scanner(System.in);
        while (true) {
            LOG.debug("> ");
            String userText = in.nextLine();
            if (List.of("quit", "exit").contains(userText)) {
                break;
            }
            if (state == null) {
                state = model.createNewState();
            }
            conversationTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, userText)));
            conversationTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
            Set<Integer> stopTokens = chatFormat.getStopTokens();
            List<Integer> responseTokens = Llama.generateTokens(model, state, startPosition, conversationTokens.subList(startPosition, conversationTokens.size()), stopTokens, options.maxTokens(), sampler, options.echo(), token -> {
                if (options.stream()) {
                    if (model.tokenizer().isNotSpecialToken(token)) {
                        LOG.debug(model.tokenizer().decode(List.of(token)));
                    }
                }
            });
            // Include stop token in the prompt history, but not in the response displayed to the user.
            conversationTokens.addAll(responseTokens);
            startPosition = conversationTokens.size();
            Integer stopToken = null;
            if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
                stopToken = responseTokens.getLast();
                responseTokens.removeLast();
            }
            if (!options.stream()) {
                String responseText = model.tokenizer().decode(responseTokens);
                LOG.debug(responseText);
            }
            if (stopToken == null) {
                LOG.error("Ran out of context length...");
                break;
            }
        }
    }

    /**
     * Run instruct one.
     *
     * @param model   the Llama model
     * @param sampler the sampler
     * @param options the options
     */
    public static  RequestResponse runInstructOnce( Llama model,
                                                           Sampler sampler,
                                                            Options options) {
        LOG.debug("Running instruct once");
        StringBuffer buffer = new StringBuffer();
        Llama.State state = model.createNewState();
        ChatFormat chatFormat = new ChatFormat(model.tokenizer());

        List<Integer> promptTokens = new ArrayList<>();
        promptTokens.add(chatFormat.beginOfText);
        if (options.systemPrompt() != null) {
            promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, options.systemPrompt())));
        }
        promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, options.prompt())));
        promptTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));

        Set<Integer> stopTokens = chatFormat.getStopTokens();
        List<Integer> responseTokens = Llama.generateTokens(model, state, 0, promptTokens, stopTokens, options.maxTokens(), sampler, options.echo(), token -> {
            String decode = model.tokenizer().decode(List.of(token));
            buffer.append(decode);
            LOG.debug(decode);

            // TODO Double check if this is still required when using buffer.append ?
//            if (options.stream()) {
//                if (model.tokenizer().isNotSpecialToken(token)) {
//                    LOG.debug(decode);
//                }
//            }
        });

        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
            responseTokens.removeLast();
        }

        if (!options.stream()) {
            String responseText = model.tokenizer().decode(responseTokens);
            LOG.debug(responseText);
        }

        return new RequestResponse(responseTokens.size(), buffer.toString());
    }

    public record Options(Path modelPath,
                          String prompt,
                          String systemPrompt,
                          boolean interactive,
                          float temperature,
                          float topp,
                          long seed,
                          int maxTokens,
                          boolean stream,
                          boolean echo) {
    }
}

final class GGUF {
    private static final int GGUF_MAGIC = 0x46554747;
    private static final int DEFAULT_ALIGNMENT = 32; // must be a power of 2
    private static final List<Integer> SUPPORTED_GGUF_VERSIONS = List.of(2, 3);
    private int tensorCount; // uint64_t
    private int alignment;
    private Map<String, Object> metadata;

    public Map<String, GGUFTensorInfo> getTensorInfos() {
        return tensorInfos;
    }

    private Map<String, GGUFTensorInfo> tensorInfos;

    private long tensorDataOffset;

    public long getTensorDataOffset() {
        return tensorDataOffset;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    private final ByteBuffer BB_1 = ByteBuffer.allocate(Byte.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer BB_2 = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer BB_4 = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer BB_8 = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);

    public static  GGUF loadModel(Path modelPath) throws IOException {
        try (FileChannel fileChannel = FileChannel.open(modelPath);
             var ignored = Timer.log("Parse " + modelPath)) {
            GGUF gguf = new GGUF();
            gguf.loadModelImpl(fileChannel);
            return gguf;
        }
    }

    enum MetadataValueType {
        // The value is a 8-bit unsigned integer.
        UINT8(1),
        // The value is a 8-bit signed integer.
        INT8(1),
        // The value is a 16-bit unsigned little-endian integer.
        UINT16(2),
        // The value is a 16-bit signed little-endian integer.
        INT16(2),
        // The value is a 32-bit unsigned little-endian integer.
        UINT32(4),
        // The value is a 32-bit signed little-endian integer.
        INT32(4),
        // The value is a 32-bit IEEE754 floating point number.
        FLOAT32(4),
        // The value is a boolean.
        // 1-byte value where 0 is false and 1 is true.
        // Anything else is invalid, and should be treated as either the model being invalid or the reader being buggy.
        BOOL(1),
        // The value is a UTF-8 non-null-terminated string, with length prepended.
        STRING(-8),
        // The value is an array of other values, with the length and type prepended.
        // Arrays can be nested, and the length of the array is the number of elements in the array, not the number of bytes.
        ARRAY(-8),
        // The value is a 64-bit unsigned little-endian integer.
        UINT64(8),
        // The value is a 64-bit signed little-endian integer.
        INT64(8),
        // The value is a 64-bit IEEE754 floating point number.
        FLOAT64(8);
        private final int byteSize;

        MetadataValueType(int byteSize) {
            this.byteSize = byteSize;
        }

        private static final MetadataValueType[] VALUES = values();

        public static MetadataValueType fromIndex(int index) {
            return VALUES[index];
        }

        public int byteSize() {
            return byteSize;
        }
    }

    private void loadModelImpl(FileChannel fileChannel) throws IOException {
        // The header of the file.
        readHeader(fileChannel); // gguf_header_t header;
        // Tensor infos, which can be used to locate the tensor data.
        // gguf_tensor_info_t tensor_infos[header.tensor_count];
        this.tensorInfos = HashMap.newHashMap(tensorCount);
        for (int i = 0; i < tensorCount; ++i) {
            GGUF.GGUFTensorInfo ti = readTensorInfo(fileChannel);
            assert !tensorInfos.containsKey(ti.name);
            tensorInfos.put(ti.name, ti);
        }
        // Padding to the nearest multiple of `ALIGNMENT`.
        // uint8_t _padding[ALIGNMENT - (sizeof(header + tensor_infos) % ALIGNMENT)];
        //long _padding = -fileChannel.position() & (ALIGNMENT - 1);
        long _padding = getAlignment() - (fileChannel.position() % getAlignment());
        fileChannel.position(fileChannel.position() + _padding);
        // Tensor data.
        //
        // This is arbitrary binary data corresponding to the weights of the model. This data should be close
        // or identical to the data in the original model file, but may be different due to quantization or
        // other optimizations for inference. Any such deviations should be recorded in the metadata or as
        // part of the architecture definition.
        //
        // Each tensor's data must be stored within this array, and located through its `tensor_infos` entry.
        // The offset of each tensor's data must be a multiple of `ALIGNMENT`, and the space between tensors
        // should be padded to `ALIGNMENT` bytes.
        // uint8_t tensor_data[];
        this.tensorDataOffset = fileChannel.position();
    }

    public static  Map<String, GGMLTensorEntry> loadTensors( FileChannel fileChannel,
                                                                    long tensorDataOffset,
                                                                     Map<String, GGUFTensorInfo> tensorInfos) throws IOException {
        Arena arena = Arena.ofAuto();
        MemorySegment tensorData = fileChannel.map(FileChannel.MapMode.READ_ONLY, tensorDataOffset, fileChannel.size() - tensorDataOffset, arena);
        Map<String, GGMLTensorEntry> tensorEntries = HashMap.newHashMap(tensorInfos.size());
        for (Map.Entry<String, GGUFTensorInfo> entry : tensorInfos.entrySet()) {
            GGUFTensorInfo ti = entry.getValue();
            int numberOfElements = FloatTensor.numberOfElements(ti.dimensions());
            int sizeInBytes = Math.toIntExact(ti.ggmlType().byteSizeFor(numberOfElements));
            MemorySegment memorySegment = tensorData.asSlice(ti.offset(), sizeInBytes);
            tensorEntries.put(ti.name(), new GGMLTensorEntry(tensorData, ti.name(), ti.ggmlType(), ti.dimensions(), memorySegment));
        }
        return tensorEntries;
    }

    public record GGUFTensorInfo(String name, int[] dimensions, GGMLType ggmlType, long offset) {
    }

    private GGMLType readGGMLType(FileChannel fileChannel) throws IOException {
        int ggmlTypeId = readInt(fileChannel); // ggml_type type;
        return GGMLType.fromId(ggmlTypeId);
    }

    
    private GGUF. GGUFTensorInfo readTensorInfo(FileChannel fileChannel) throws IOException {
        // The name of the tensor. It is a standard GGUF string, with the caveat that
        // it must be at most 64 bytes long.
        String name = readString(fileChannel); // gguf_string_t name;
        assert name.length() <= 64;
        // The number of dimensions in the tensor.
        // Currently at most 4, but this may change in the future.
        int n_dimensions = readInt(fileChannel); // uint32_t n_dimensions;
        assert n_dimensions <= 4;
        // The dimensions of the tensor.
        int[] dimensions = new int[n_dimensions]; // uint64_t dimensions[n_dimensions];
        for (int i = 0; i < n_dimensions; ++i) {
            dimensions[i] = Math.toIntExact(readLong(fileChannel));
        }
        // The type of the tensor.
        GGMLType ggmlType = readGGMLType(fileChannel); // ggml_type type;
        // The offset of the tensor's data in this file in bytes.
        // This offset is relative to `tensor_data`, not to the start
        // of the file, to make it easier for writers to write the file.
        // Readers should consider exposing this offset relative to the
        // file to make it easier to read the data.
        // Must be a multiple of `ALIGNMENT`.
        long offset = readLong(fileChannel); // uint64_t offset;
        if (offset % getAlignment() != 0) throw new AssertionError();
        return new GGUF.GGUFTensorInfo(name, dimensions, ggmlType, offset);
    }

    
    private  String readString(FileChannel fileChannel) throws IOException {
        // A string in GGUF.
        // The length of the string, in bytes.
        int len = Math.toIntExact(readLong(fileChannel)); // uint64_t len;
        // The string as a UTF-8 non-null-terminated string.
        byte[] bytes = new byte[len]; // char string[len];
        int bytesRead = fileChannel.read(ByteBuffer.wrap(bytes));
        assert len == bytesRead;
        return new String(bytes, StandardCharsets.UTF_8);
    }

    
    private  Pair<String, Object> readKeyValuePair(FileChannel fileChannel) throws IOException {
        // The key of the metadata. It is a standard GGUF string, with the following caveats:
        // - It must be a valid ASCII string.
        // - It must be a hierarchical key, where each segment is `lower_snake_case` and separated by a `.`.
        // - It must be at most 2^16-1/65535 bytes long.
        // Any keys that do not follow these rules are invalid.
        String key = readString(fileChannel); // gguf_string_t key;
        assert key.length() < (1 << 16);
        assert key.codePoints().allMatch(cp -> ('a' <= cp && cp <= 'z') || ('0' <= cp && cp <= '9') || cp == '_' || cp == '.');
        Object value = readMetadataValue(fileChannel);
        return new Pair<>(key, value);
    }

    private Object readMetadataValue(FileChannel fileChannel) throws IOException {
        // The type of the value.
        // Must be one of the `gguf_metadata_value_type` values.
        MetadataValueType value_type = readMetadataValueType(fileChannel); // gguf_metadata_value_type value_type;
        // The value.
        return readMetadataValueOfType(value_type, fileChannel); // gguf_metadata_value_t value;
    }

    void readHeader(FileChannel fileChannel) throws IOException {
        // Magic number to announce that this is a GGUF file.
        // Must be `GGUF` at the byte level: `0x47` `0x47` `0x55` `0x46`.
        // Your executor might do little-endian byte order, so it might be
        // check for 0x46554747 and letting the endianness cancel out.
        // Consider being *very* explicit about the byte order here.
        int magic = readInt(fileChannel); //    uint32_t magic;
        if (magic != GGUF_MAGIC) {
            throw new IllegalArgumentException("unsupported header.magic " + magic);
        }
        // The version of the format implemented.
        // Must be `3` for version described in this spec.
        //
        // This version should only be increased for structural changes to the format.
        // Changes that do not affect the structure of the file should instead update the metadata
        // to signify the change.
        int version = readInt(fileChannel); // uint32_t version;
        if (!SUPPORTED_GGUF_VERSIONS.contains(version)) {
            throw new IllegalArgumentException("unsupported header.version " + version);
        }
        // The number of tensors in the file.
        // This is explicit, instead of being included in the metadata, to ensure it is always present
        // for loading the tensors.
        this.tensorCount = Math.toIntExact(readLong(fileChannel)); // uint64_t tensor_count;
        // The number of metadata key-value pairs.
        // uint64_t
        int metadata_kv_count = Math.toIntExact(readLong(fileChannel)); // uint64_t metadata_kv_count;
        // The metadata key-value pairs.
        // gguf_metadata_kv_t metadata_kv[metadata_kv_count];
        this.metadata = HashMap.newHashMap(metadata_kv_count);
        for (int i = 0; i < metadata_kv_count; ++i) {
            Pair<String, Object> keyValue = readKeyValuePair(fileChannel);
            assert !metadata.containsKey(keyValue.first());
            metadata.put(keyValue.first(), keyValue.second());
        }
    }

    private  Object readArray(FileChannel fileChannel) throws IOException {
        // Any value type is valid, including arrays.
        MetadataValueType value_type = readMetadataValueType(fileChannel); // gguf_metadata_value_type type;
        // Number of elements, not bytes
        int len = Math.toIntExact(readLong(fileChannel)); // uint64_t len;
        // The array of values.
        // gguf_metadata_value_t array[len];
        switch (value_type) {
            case UINT8, INT8 -> {
                byte[] bytes = new byte[len];
                for (int i = 0; i < len; ++i) {
                    bytes[i] = readByte(fileChannel);
                }
                return bytes;
            }
            case UINT16, INT16 -> {
                short[] shorts = new short[len];
                for (int i = 0; i < len; ++i) {
                    shorts[i] = readShort(fileChannel);
                }
                return shorts;
            }
            case UINT32, INT32 -> {
                int[] ints = new int[len];
                for (int i = 0; i < len; ++i) {
                    ints[i] = readInt(fileChannel);
                }
                return ints;
            }
            case FLOAT32 -> {
                float[] floats = new float[len];
                for (int i = 0; i < len; ++i) {
                    floats[i] = readFloat(fileChannel);
                }
                return floats;
            }
            case BOOL -> {
                boolean[] booleans = new boolean[len];
                for (int i = 0; i < len; ++i) {
                    booleans[i] = readBoolean(fileChannel);
                }
                return booleans;
            }
            case STRING -> {
                String[] strings = new String[len];
                for (int i = 0; i < len; ++i) {
                    strings[i] = readString(fileChannel);
                }
                return strings;
            }
            case ARRAY -> {
                Object[] arrays = new Object[len];
                for (int i = 0; i < len; ++i) {
                    arrays[i] = readArray(fileChannel);
                }
                return arrays;
            }
            default -> throw new UnsupportedOperationException("read array of " + value_type);
        }
    }

    private Object readMetadataValueOfType( MetadataValueType valueType,
                                           FileChannel fileChannel) throws IOException {
        return switch (valueType) {
            case UINT8, INT8 -> readByte(fileChannel);
            case UINT16, INT16 -> readShort(fileChannel);
            case UINT32, INT32 -> readInt(fileChannel);
            case FLOAT32 -> readFloat(fileChannel);
            case UINT64, INT64 -> readLong(fileChannel);
            case FLOAT64 -> readDouble(fileChannel);
            case BOOL -> readBoolean(fileChannel);
            case STRING -> readString(fileChannel);
            case ARRAY -> readArray(fileChannel);
        };
    }

    private byte readByte( FileChannel fileChannel) throws IOException {
        int bytesRead = fileChannel.read(BB_1);
        assert bytesRead == 1;
        return BB_1.clear().get(0);
    }

    private boolean readBoolean(FileChannel fileChannel) throws IOException {
        return readByte(fileChannel) != 0;
    }

    private short readShort( FileChannel fileChannel) throws IOException {
        int bytesRead = fileChannel.read(BB_2);
        assert bytesRead == 2;
        return BB_2.clear().getShort(0);
    }

    private int readInt( FileChannel fileChannel) throws IOException {
        int bytesRead = fileChannel.read(BB_4);
        assert bytesRead == 4;
        return BB_4.clear().getInt(0);
    }

    private long readLong( FileChannel fileChannel) throws IOException {
        int bytesRead = fileChannel.read(BB_8);
        assert bytesRead == 8;
        return BB_8.clear().getLong(0);
    }

    private float readFloat(FileChannel fileChannel) throws IOException {
        return Float.intBitsToFloat(readInt(fileChannel));
    }

    private double readDouble(FileChannel fileChannel) throws IOException {
        return Double.longBitsToDouble(readLong(fileChannel));
    }

    private MetadataValueType readMetadataValueType(FileChannel fileChannel) throws IOException {
        int index = readInt(fileChannel);
        return MetadataValueType.fromIndex(index);
    }

    public int getAlignment() {
        if (alignment != 0) {
            return alignment;
        }
        alignment = (int) metadata.getOrDefault("general.alignment", DEFAULT_ALIGNMENT);
        assert Integer.bitCount(alignment) == 1 : "alignment must be a power of two";
        return alignment;
    }
}

interface Timer extends AutoCloseable {
    Logger LOGGER = LoggerFactory.getLogger(Timer.class);

    @Override
    void close(); // no Exception

    
    static  Timer log(String label) {
        return log(label, TimeUnit.MILLISECONDS);
    }

    
    static  Timer log(String label, TimeUnit timeUnit) {
        return new Timer() {
            final long startNanos = System.nanoTime();

            @Override
            public void close() {
                long elapsedNanos = System.nanoTime() - startNanos;
                LOGGER.debug("{}: {} {}",
                        label,
                        timeUnit.convert(elapsedNanos, TimeUnit.NANOSECONDS),
                        timeUnit.toChronoUnit().name().toLowerCase());
            }
        };
    }
}

/**
 * Byte Pair Encoding tokenizer.
 * <p>
 * Based on <a href="https://github.com/karpathy/minbpe">minbpe</a>, algorithmically follows along the
 * <a href="https://github.com/openai/gpt-2/blob/master/src/encoder.py">GPT 2 tokenizer</a>
 */
class Tokenizer {
    private final Pattern compiledPattern;
    private final Vocabulary vocabulary;
    private final Map<Pair<Integer, Integer>, Integer> merges;
    private final Map<String, Integer> specialTokens;

    public Map<String, Integer> getSpecialTokens() {
        return specialTokens;
    }

    public boolean isNotSpecialToken(int tokenIndex) {
        return !specialTokens.containsValue(tokenIndex);
    }

    public Tokenizer(Vocabulary vocabulary,
                      List<Pair<Integer, Integer>> merges,
                     String regexPattern,
                     Map<String, Integer> specialTokens) {
        this.vocabulary = vocabulary;
        this.compiledPattern = regexPattern != null ? Pattern.compile(regexPattern) : null;
        this.specialTokens = new HashMap<>(specialTokens);
        this.merges = new HashMap<>();
        for (Pair<Integer, Integer> pair : merges) {
            int firstIndex = pair.first();
            int secondIndex = pair.second();
            int mergeIndex = vocabulary.getIndex(vocabulary.get(firstIndex) + vocabulary.get(secondIndex)).orElseThrow();
            this.merges.put(pair, mergeIndex);
        }
    }

    private int[] encodeImpl(String text) {
        return encode(text, Set.of()).stream().mapToInt(i -> i).toArray();
    }

    /**
     * Unlike {@link #encodeOrdinary(String)}, this function handles special tokens.
     * allowed_special: can be "all"|"none"|"none_raise" or a custom set of special tokens
     * if none_raise, then an error is raised if any special token is encountered in text
     * this is the default tiktoken behavior right now as well
     * any other behavior is either annoying or a major footgun.
     */
    List<Integer> encode(String text, Set<String> allowedSpecial) {
        // decode the user desire w.r.t. handling of special tokens
        assert getSpecialTokens().keySet().containsAll(allowedSpecial);
        if (allowedSpecial.isEmpty()) {
            // shortcut: if no special tokens, just use the ordinary encoding
            return encodeOrdinary(text);
        }

        // otherwise, we have to be careful with potential special tokens in text
        // we handle special tokens by splitting the text
        // based on the occurrence of any exact match with any of the special tokens
        // we can use re.split for this. note that surrounding the pattern with ()
        // makes it into a capturing group, so the special tokens will be included
        String specialPattern = allowedSpecial
                .stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|", "(", ")"));

        String[] specialChunks = text.split(specialPattern);
        // now all the special characters are separated from the rest of the text
        // all chunks of text are encoded separately, then results are joined
        List<Integer> ids = new ArrayList<>();
        for (String part : specialChunks) {
            if (allowedSpecial.contains(part)) {
                // this is a special token, encode it separately as a special case
                ids.add(getSpecialTokens().get(part));
            } else {
                // this is an ordinary sequence, encode it normally
                ids.addAll(encodeOrdinary(part));
            }
        }
        return ids;
    }

    private static  List<String> findAll( Pattern pattern, String text) {
        List<String> allMatches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            allMatches.add(matcher.group());
        }
        return allMatches;
    }

    /**
     * Encoding that ignores any special tokens.
     */
    public List<Integer> encodeOrdinary(String text) {
        // split text into chunks of text by categories defined in regex pattern
        List<String> textChunks = findAll(compiledPattern, text);
        // all chunks of text are encoded separately, then results are joined
        List<Integer> ids = new ArrayList<>();
        for (String chunk : textChunks) {
            List<Integer> chunkIds = encodeChunk(chunk);
            ids.addAll(chunkIds);
        }
        return ids;
    }

    private  Map<Pair<Integer, Integer>, Integer> getStats( List<Integer> ids) {
        Map<Pair<Integer, Integer>, Integer> map = new HashMap<>();
        for (int i = 0; i + 1 < ids.size(); i++) {
            Pair<Integer, Integer> key = new Pair<>(ids.get(i), ids.get(i + 1));
            map.put(key, map.getOrDefault(key, 0) + 1);
        }
        return map;
    }

    private List<Integer> encodeChunk( String chunk) {
        // return the token ids
        // let's begin. first, convert all bytes to integers in range 0..255
        List<Integer> ids = new ArrayList<>();
        for (int b : chunk.toCharArray()) {
            int tokenIndex = this.vocabulary.getIndex(String.valueOf((char) b)).orElseThrow();
            ids.add(tokenIndex);
        }

        while (ids.size() >= 2) {
            // find the pair with the lowest merge index
            Map<Pair<Integer, Integer>, Integer> stats = getStats(ids);
            Pair<Integer, Integer> pair = stats.keySet().stream().min(Comparator.comparingInt(key -> this.merges.getOrDefault(key, Integer.MAX_VALUE))).orElseThrow();
            // subtle: if there are no more merges available, the key will
            // result in an inf for every single pair, and the min will be
            // just the first pair in the list, arbitrarily
            // we can detect this terminating case by a membership check
            if (!this.merges.containsKey(pair)) {
                break; // nothing else can be merged anymore
            }
            // otherwise let's merge the best pair (lowest merge index)
            int idx = this.merges.get(pair);
            ids = merge(ids, pair, idx);
        }
        return ids;
    }

    private static  List<Integer> merge( List<Integer> ids,
                                                Pair<Integer, Integer> pair,
                                                int idx) {
        List<Integer> newids = new ArrayList<>();
        int i = 0;
        while (i < ids.size()) {
            // if not at the very last position AND the pair matches, replace it
            if (ids.get(i).equals(pair.first()) && i < ids.size() - 1 && ids.get(i + 1).equals(pair.second())) {
                newids.add(idx);
                i += 2;
            } else {
                newids.add(ids.get(i));
                i += 1;
            }
        }
        return newids;
    }

    public String decodeImpl( List<Integer> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int token : tokens) {
            String tokenString = vocabulary.get(token);
            sb.append(tokenString);
        }
        return sb.toString();
    }

    /**
     * Returns list of utf-8 byte and a corresponding list of unicode strings.
     * The reversible bpe codes work on unicode strings.
     * This means you need a large # of unicode characters in your vocab if you want to avoid UNKs.
     * When you're at something like a 10B token dataset you end up needing around 5K for decent coverage.
     * This is a significant percentage of your normal, say, 32K bpe vocab.
     * To avoid that, we want lookup tables between utf-8 bytes and unicode strings.
     * And avoids mapping to whitespace/control characters the bpe code barfs on.
     */
    private static Map<Integer, Integer> bytesToUnicode() {
        List<Integer> bs = new ArrayList<>();
        IntStream.rangeClosed('!', '~').forEach(bs::add);
        IntStream.rangeClosed('¡', '¬').forEach(bs::add);
        IntStream.rangeClosed('®', 'ÿ').forEach(bs::add);

        List<Integer> cs = new ArrayList<>(bs);
        int n = 0;
        for (int b = 0; b < 256; ++b) {
            if (!bs.contains(b)) {
                bs.add(b);
                cs.add(256 + n);
                n += 1;
            }
        }

        // return dict(zip(bs, cs))
        return IntStream.range(0, bs.size())
                .boxed()
                .collect(Collectors.toMap(bs::get, cs::get));
    }

    static final Map<Integer, Integer> BYTE_ENCODER = bytesToUnicode();
    static final Map<Integer, Integer> BYTE_DECODER = BYTE_ENCODER.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    public int[] encode( String text) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            sb.appendCodePoint(BYTE_ENCODER.get(Byte.toUnsignedInt(b)));
        }
        return encodeImpl(sb.toString());
    }

    public static  String replaceControlCharacters(int  [] codePoints) {
        // we don't want to print control characters
        // which distort the output (e.g. \n or much worse)
        // https://stackoverflow.com/questions/4324790/removing-control-characters-from-a-string-in-python/19016117#19016117
        // http://www.unicode.org/reports/tr44/#GC_Values_Table\
        StringBuilder chars = new StringBuilder();
        for (int cp : codePoints) {
            if (Character.getType(cp) == Character.CONTROL && cp != '\n') {
                chars.append("\\u").append(HexFormat.of().toHexDigits(cp, 4)); // escape
            } else {
                chars.appendCodePoint(cp); // this character is ok
            }
        }
        return chars.toString();
    }

    public static  String replaceControlCharacters( String str) {
        return replaceControlCharacters(str.codePoints().toArray());
    }

    public List<Integer> encodeAsList(String text) {
        return Arrays.stream(encode(text)).boxed().toList();
    }

    public String decode(List<Integer> tokens) {
        String decoded = decodeImpl(tokens);
        int[] decodedBytesAsInts = decoded.codePoints().map(BYTE_DECODER::get).toArray();
        byte[] rawBytes = new byte[decodedBytesAsInts.length];
        for (int i = 0; i < decoded.length(); i++) {
            rawBytes[i] = (byte) decodedBytesAsInts[i];
        }
        return new String(rawBytes, StandardCharsets.UTF_8);
    }
}

final class Parallel {
    public static void parallelFor(int startInclusive, int endExclusive, IntConsumer action) {
        IntStream.range(startInclusive, endExclusive).parallel().forEach(action);
    }
}

record Pair<First, Second>(First first, Second second) {
}

record GGMLTensorEntry(MemorySegment mappedFile,
                       String name, GGMLType ggmlType, int[] shape,
                       MemorySegment memorySegment) {
}

final class Float16 {
    public static final int BYTES = 2;
}

enum GGMLType {
    F32(Float.BYTES),
    F16(Float16.BYTES),
    Q4_0(Float16.BYTES + 16 * Byte.BYTES, 32),
    Q4_1(2 * Float16.BYTES + 16 * Byte.BYTES, 32),
    UNSUPPORTED_Q4_2(Integer.MAX_VALUE), // support has been removed
    UNSUPPORTED_Q4_3(Integer.MAX_VALUE), // support has been removed
    Q5_0(Integer.MAX_VALUE),
    Q5_1(Integer.MAX_VALUE),
    Q8_0(Float16.BYTES + 32 * Byte.BYTES, 32),
    Q8_1(32 * Byte.BYTES + 2 * Float.BYTES, 32),
    // k-quantization's
    Q2_K(Integer.MAX_VALUE),
    Q3_K(Integer.MAX_VALUE),
    Q4_K(2 * Float16.BYTES + ((GGMLType.QK_K / 16) / 8 * 6) + GGMLType.QK_K / 2, GGMLType.QK_K),
    Q5_K(2 * Float16.BYTES + ((GGMLType.QK_K / 16) / 8 * 6) + GGMLType.QK_K / 8 + GGMLType.QK_K / 2, GGMLType.QK_K),
    Q6_K(GGMLType.QK_K / 2 + GGMLType.QK_K / 4 + GGMLType.QK_K / 16 + Float16.BYTES, GGMLType.QK_K),
    Q8_K(Integer.MAX_VALUE),
    I8(Byte.BYTES),
    I16(Short.BYTES),
    I32(Integer.BYTES);

    private static final GGMLType[] VALUES = values();

    private final int typeSize;

    private final int blockSize;

    public int getTypeSize() {
        return typeSize;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public static GGMLType fromId(int id) {
        return VALUES[id];
    }

    GGMLType(int typeSize) {
        this(typeSize, 1);
    }

    public long byteSizeFor(int numberOfElements) {
        long t = numberOfElements * (long) getTypeSize();
        assert t % getBlockSize() == 0;
        return Math.toIntExact(t / getBlockSize());
    }

    public static final int QK_K = 256; // or 64?

    GGMLType(int typeSize, int blockSize) {
        assert blockSize > 0;
        assert typeSize > 0;
        assert isPowerOf2(blockSize);
        this.typeSize = typeSize;
        this.blockSize = blockSize;
    }

    private static boolean isPowerOf2(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}

/**
 * {@link FloatTensor} quantized in the {@link GGMLType#Q4_0} format.
 * <p>
 * This tensor implementation is not compatible with {@link FloatTensor}, but
 * {@link #dot(int, FloatTensor, int, int)} has a vectorized implementation that is used when
 * the second argument implements {@link FloatTensor}.
 */
final class Q4_0FloatTensor extends FloatTensor {

    final int size;
    final MemorySegment memorySegment;

    public Q4_0FloatTensor(int size, MemorySegment memorySegment) {
        this.size = size;
        this.memorySegment = memorySegment;
    }

    @Override
    int size() {
        return size;
    }

    @Override
    public void setFloat(int index, float value) {
        throw new UnsupportedOperationException("setFloat");
    }

    @Override
    FloatVector getFloatVector(VectorSpecies<Float> species, int index) {
        throw new UnsupportedOperationException("getFloatVector");
    }

    @Override
    public GGMLType type() {
        return GGMLType.Q4_0;
    }

    @Override
    public float getFloat(int index) {
        assert 0 <= index && index < size;
        int blockIndex = index / GGMLType.Q4_0.getBlockSize();
        int blockOffset = blockIndex * GGMLType.Q4_0.getTypeSize();
        float scale = Float.float16ToFloat(readShort(memorySegment, blockOffset));
        byte quant;
        int modIndex = index % GGMLType.Q4_0.getBlockSize();
        if (modIndex < GGMLType.Q4_0.getBlockSize() / 2) {
            quant = (byte) (readByte(memorySegment, blockOffset + Float16.BYTES + modIndex) & 0x0F);
        } else {
            quant = (byte) ((readByte(memorySegment, blockOffset + Float16.BYTES + modIndex - GGMLType.Q4_0.getBlockSize() / 2) >>> 4) & 0x0F);
        }
        quant -= 8;
        return quant * scale;
    }

    @Override
    public float dot(int thisOffset, FloatTensor that, int thatOffset, int size) {
        if (FloatTensor.USE_VECTOR_API) {
            return vectorDot(this, thisOffset, (ArrayFloatTensor) that, thatOffset, size);
        } else {
            return FloatTensor.scalarDot(this, thisOffset, that, thatOffset, size);
        }
    }

    private static float vectorDot(Q4_0FloatTensor thiz,
                                   int thisOffset,
                                   ArrayFloatTensor that,
                                   int thatOffset,
                                   int size) {
        float result = 0f;
        int j = 0;

        // Align thisOffset + j to type().getBlockSize().
        assert Integer.bitCount(GGMLType.Q4_0.getBlockSize()) == 1 : "power of 2";
        int alignmentBound = Math.min(size, -thisOffset & (GGMLType.Q4_0.getBlockSize() - 1));
        if (alignmentBound > 0) {
            result += FloatTensor.scalarDot(thiz, thisOffset, that, thatOffset, alignmentBound);
            j += alignmentBound;
        }
        assert (thisOffset + j) % GGMLType.Q4_0.getBlockSize() == 0;

        FloatVector val = FloatVector.zero(F_SPECIES);
        int blockOffset = (thisOffset + j) / GGMLType.Q4_0.getBlockSize() * GGMLType.Q4_0.getTypeSize();
        int upperBound = size / GGMLType.Q4_0.getBlockSize() * GGMLType.Q4_0.getBlockSize();
        for (; j < upperBound; j += GGMLType.Q4_0.getBlockSize(), blockOffset += GGMLType.Q4_0.getTypeSize()) {
            float wScaleValue = Float.float16ToFloat(readShort(thiz.memorySegment, blockOffset));
            var wScale = FloatVector.broadcast(F_SPECIES, wScaleValue);
            var B_SPECIES = ByteVector.SPECIES_128;
            var wBytes = ByteVector.fromMemorySegment(B_SPECIES, thiz.memorySegment, blockOffset + Float16.BYTES, ByteOrder.LITTLE_ENDIAN);
            var loBytes = wBytes.and((byte) 0xF).sub((byte) 8);
            var hiBytes = wBytes.lanewise(VectorOperators.LSHR, 4).sub((byte) 8);
            if (F_SPECIES.vectorBitSize() == 256) {
                var sum0 = that.getFloatVector(F_SPECIES, thatOffset + j).mul(loBytes.castShape(F_SPECIES, 0));
                var sum1 = that.getFloatVector(F_SPECIES, thatOffset + j + F_SPECIES.length()).mul(loBytes.castShape(F_SPECIES, 1));
                var sum2 = that.getFloatVector(F_SPECIES, thatOffset + j + 2 * F_SPECIES.length()).mul(hiBytes.castShape(F_SPECIES, 0));
                var sum3 = that.getFloatVector(F_SPECIES, thatOffset + j + 3 * F_SPECIES.length()).mul(hiBytes.castShape(F_SPECIES, 1));
                val = sum0.add(sum1).add(sum2).add(sum3).fma(wScale, val);
            } else if (F_SPECIES.vectorBitSize() == 128) {
                // This loop cannot be unrolled, why?
                for (int i = 0; i < 2; ++i) {
                    var tmp = i == 0 ? loBytes : hiBytes;
                    var sum0 = that.getFloatVector(F_SPECIES, thatOffset + j + (i * 4) * F_SPECIES.length()).mul(tmp.castShape(F_SPECIES, 0));
                    var sum1 = that.getFloatVector(F_SPECIES, thatOffset + j + (i * 4 + 1) * F_SPECIES.length()).mul(tmp.castShape(F_SPECIES, 1));
                    var sum2 = that.getFloatVector(F_SPECIES, thatOffset + j + (i * 4 + 2) * F_SPECIES.length()).mul(tmp.castShape(F_SPECIES, 2));
                    var sum3 = that.getFloatVector(F_SPECIES, thatOffset + j + (i * 4 + 3) * F_SPECIES.length()).mul(tmp.castShape(F_SPECIES, 3));
                    val = sum0.add(sum1).add(sum2).add(sum3).fma(wScale, val);
                }
            } else {
                throw new UnsupportedOperationException(F_SPECIES.toString());
            }
        }
        result += val.reduceLanes(VectorOperators.ADD);

        // Remaining entries.
        if (j < size) {
            result += FloatTensor.scalarDot(thiz, thisOffset + j, that, thatOffset + j, size - j);
        }

        return result;
    }
}

final class Q8_0FloatTensor extends FloatTensor {

    final int size;
    final MemorySegment memorySegment;

    public Q8_0FloatTensor(int size, MemorySegment memorySegment) {
        this.size = size;
        this.memorySegment = memorySegment;
    }

    @Override
    int size() {
        return size;
    }

    @Override
    public void setFloat(int index, float value) {
        throw new UnsupportedOperationException("setFloat");
    }

    @Override
    FloatVector getFloatVector(VectorSpecies<Float> species, int index) {
        throw new UnsupportedOperationException("getFloatVector");
    }

    @Override
    public GGMLType type() {
        return GGMLType.Q8_0;
    }

    @Override
    public float getFloat(int index) {
        assert 0 <= index && index < size;
        int blockIndex = index / GGMLType.Q8_0.getBlockSize();
        int withinBlockIndex = index % GGMLType.Q8_0.getBlockSize();
        int blockOffset = blockIndex * GGMLType.Q8_0.getTypeSize();
        byte quant = readByte(memorySegment, blockOffset + Float16.BYTES + withinBlockIndex);
        float scale = Float.float16ToFloat(readShort(memorySegment, blockOffset));
        return quant * scale;
    }

    @Override
    public float dot(int thisOffset, FloatTensor that, int thatOffset, int size) {
        if (FloatTensor.USE_VECTOR_API) {
            return vectorDot(this, thisOffset, (ArrayFloatTensor) that, thatOffset, size);
        } else {
            return FloatTensor.scalarDot(this, thisOffset, that, thatOffset, size);
        }
    }

    private static float vectorDot(Q8_0FloatTensor thiz, int thisOffset, ArrayFloatTensor that, int thatOffset, int size) {
        float result = 0f;
        int j = 0;

        // Align thisOffset + startIndex to type().getBlockSize().
        assert Integer.bitCount(GGMLType.Q8_0.getBlockSize()) == 1 : "power of 2";
        int alignmentBound = Math.min(size, -thisOffset & (GGMLType.Q8_0.getBlockSize() - 1));
        if (alignmentBound > 0) {
            result += FloatTensor.scalarDot(thiz, thisOffset, that, thatOffset, alignmentBound);
            j += alignmentBound;
        }
        assert (thisOffset + j) % GGMLType.Q8_0.getBlockSize() == 0;

        FloatVector val = FloatVector.zero(F_SPECIES);
        int blockOffset = (thisOffset + j) / GGMLType.Q8_0.getBlockSize() * GGMLType.Q8_0.getTypeSize();
        int upperBound = size / GGMLType.Q8_0.getBlockSize() * GGMLType.Q8_0.getBlockSize();
        for (; j < upperBound; j += GGMLType.Q8_0.getBlockSize(), blockOffset += GGMLType.Q8_0.getTypeSize()) {
            float wScaleValue = Float.float16ToFloat(readShort(thiz.memorySegment, blockOffset));
            var wScale = FloatVector.broadcast(F_SPECIES, wScaleValue);
            if (F_SPECIES.vectorBitSize() == 256) {
                var wBytes = ByteVector.fromMemorySegment(ByteVector.SPECIES_256, thiz.memorySegment, blockOffset + Float16.BYTES, ByteOrder.LITTLE_ENDIAN);
                var sum0 = that.getFloatVector(F_SPECIES, thatOffset + j).mul(wBytes.castShape(F_SPECIES, 0));
                var sum1 = that.getFloatVector(F_SPECIES, thatOffset + j + F_SPECIES.length()).mul(wBytes.castShape(F_SPECIES, 1));
                var sum2 = that.getFloatVector(F_SPECIES, thatOffset + j + 2 * F_SPECIES.length()).mul(wBytes.castShape(F_SPECIES, 2));
                var sum3 = that.getFloatVector(F_SPECIES, thatOffset + j + 3 * F_SPECIES.length()).mul(wBytes.castShape(F_SPECIES, 3));
                val = sum0.add(sum1).add(sum2).add(sum3).fma(wScale, val);
            } else if (F_SPECIES.vectorBitSize() == 128) {
                VectorSpecies<Byte> B_128 = ByteVector.SPECIES_128;
                // This loop cannot be unrolled, why?
                for (int i = 0; i < 2; ++i) {
                    var wBytes = ByteVector.fromMemorySegment(B_128, thiz.memorySegment, blockOffset + Float16.BYTES + i * B_128.vectorByteSize(), ByteOrder.LITTLE_ENDIAN);
                    var sum0 = that.getFloatVector(F_SPECIES, thatOffset + j + i * 16).mul(wBytes.castShape(F_SPECIES, 0));
                    var sum1 = that.getFloatVector(F_SPECIES, thatOffset + j + i * 16 + F_SPECIES.length()).mul(wBytes.castShape(F_SPECIES, 1));
                    var sum2 = that.getFloatVector(F_SPECIES, thatOffset + j + i * 16 + 2 * F_SPECIES.length()).mul(wBytes.castShape(F_SPECIES, 2));
                    var sum3 = that.getFloatVector(F_SPECIES, thatOffset + j + i * 16 + 3 * F_SPECIES.length()).mul(wBytes.castShape(F_SPECIES, 3));
                    val = sum0.add(sum1).add(sum2).add(sum3).fma(wScale, val);
                }
            } else {
                throw new UnsupportedOperationException(F_SPECIES.toString());
            }
        }
        result += val.reduceLanes(VectorOperators.ADD);

        // Remaining entries.
        if (j < size) {
            result += FloatTensor.scalarDot(thiz, thisOffset + j, that, thatOffset + j, size - j);
        }

        return result;
    }
}

final class ArrayFloatTensor extends FloatTensor {

    final float[] values;

    ArrayFloatTensor(float[] values) {
        this.values = values;
    }

    public static  FloatTensor allocate(int... dims) {
        int numberOfElements = FloatTensor.numberOfElements(dims);
        return new ArrayFloatTensor(new float[numberOfElements]);
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public float getFloat(int index) {
        return values[index];
    }

    @Override
    public void setFloat(int index, float value) {
        values[index] = value;
    }

    @Override
    public GGMLType type() {
        return GGMLType.F32;
    }

    @Override
    public FloatTensor fillInPlace(int thisOffset, int size) {
        Arrays.fill(values, thisOffset, thisOffset + size, (float) 0.0);
        return this;
    }

    @Override
    public FloatVector getFloatVector(VectorSpecies<Float> species, int index) {
        if (!USE_VECTOR_API) {
            throw new UnsupportedOperationException();
        }
        return FloatVector.fromArray(species, values, index);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}

final class RoPE {
    public static Pair<float[], float[]> precomputeFreqsCis(int contextLength,
                                                            int headSize,
                                                            double theta,
                                                            boolean ropeScaling,
                                                            float scaleFactor,
                                                            float loFreqFactor,
                                                            float hiFreqFactor,
                                                            float oldContextLength) {
        assert headSize % 2 == 0;
        float[] cr = new float[contextLength * (headSize / 2)];
        float[] ci = new float[contextLength * (headSize / 2)];
        int n = 0;
        for (int pos = 0; pos < contextLength; ++pos) {
            for (int i = 0; i < headSize; i += 2) {
                float freq = (float) (1.0 / Math.pow(theta, i / (double) headSize));
                if (ropeScaling) {
                    // Llama 3.1 scaling
                    float loFreqWavelen = oldContextLength / loFreqFactor;
                    float hiFreqWavelen = oldContextLength / hiFreqFactor;
                    float waveLen = (float) (2.0 * Math.PI / freq);
                    if (!(waveLen < hiFreqWavelen)) {
                        if (waveLen > loFreqWavelen) {
                            freq = freq / scaleFactor;
                        } else {
                            float smooth = (oldContextLength / waveLen - loFreqFactor) / (hiFreqFactor - loFreqFactor);
                            freq = (1.0f - smooth) * freq / scaleFactor + smooth * freq;
                        }
                    }
                }
                float val = pos * freq;
                cr[n] = (float) Math.cos(val);
                ci[n] = (float) Math.sin(val);
                n++;
            }
        }
        assert contextLength * (headSize / 2) == n;
        return new Pair<>(cr, ci);
    }
}

record Vocabulary(String[] tokens, float[] scores, Map<String, Integer> tokenToIndex) {
    public Vocabulary(String[] vocabulary, float[] scores) {
        this(vocabulary, scores,
                IntStream.range(0, vocabulary.length)
                        .boxed()
                        .collect(Collectors.toMap(i -> vocabulary[i], i -> i))
        );
    }

    public String get(int tokenIndex) {
        return tokens[tokenIndex];
    }

    public OptionalInt getIndex(String token) {
        Integer value = tokenToIndex.get(token);
        return value != null ? OptionalInt.of(value) : OptionalInt.empty();
    }

    public int size() {
        return tokens.length;
    }
}

record CategoricalSampler(RandomGenerator rng) implements Sampler {

    @Override
    public int sampleToken( FloatTensor logits) {
        // sample index from probabilities (they must sum to 1!)
        float random0to1 = rng.nextFloat(1f);
        float cdf = 0.0f;
        for (int i = 0; i < logits.size(); i++) {
            cdf += logits.getFloat(i);
            if (random0to1 < cdf) {
                return i;
            }
        }
        return logits.size() - 1; // in case of rounding errors
    }
}

final class ToppSampler implements Sampler {

    final int[] indices;
    final float topP;
    final RandomGenerator rng;

    public ToppSampler(int maxNumberOfElements, float topP, RandomGenerator rng) {
        this.indices = new int[maxNumberOfElements];
        this.topP = topP;
        this.rng = rng;
    }

    static void swap(int  [] array, int from, int to) {
        int tmp = array[from];
        array[from] = array[to];
        array[to] = tmp;
    }

    static void siftDown(int[] array, int from, int n, Comparator<Integer> comparator) {
        int prev = from, next;
        while ((next = 2 * prev + 1) < n) {
            int r = 2 * prev + 2;
            if (r < n && comparator.compare(array[r], array[next]) < 0) {
                next = r;
            }
            if (comparator.compare(array[next], array[prev]) < 0) {
                swap(array, prev, next);
                prev = next;
            } else {
                break;
            }
        }
    }

    @Override
    public int sampleToken( FloatTensor logits) {
        // top-p sampling (or "nucleus sampling") samples from the smallest set of
        // tokens that exceed probability topp. This way we never sample tokens that
        // have very low probabilities and are less likely to go "off the rails".
        Comparator<Integer> comparator = Comparator.comparingDouble(logits::getFloat).reversed();

        int n = logits.size();
        int head = 0;
        int tail = n - 1;
        // values smaller than (1 - topp) / (n - 1) cannot be part of the result
        // so for efficiency we crop these out as candidates before sorting
        float cutoff = (1.0f - topP) / (n - 1);
        for (int i = 0; i < indices.length; i++) {
            if (logits.getFloat(i) >= cutoff) {
                indices[head++] = i;
            } else {
                indices[tail--] = i;
            }
        }

        int n0 = head;
        // build heap O(n0)
        for (int i = n0 / 2 - 1; i >= 0; --i) {
            siftDown(indices, i, n0, comparator);
        }

        // truncate the list where cumulative probability of the largest k elements exceeds topp
        // O(k lg n0)
        float cumulativeProb = 0.0f;
        int lastIndex = 0;
        for (int i = n0 - 1; i >= 0; i--) {
            swap(indices, 0, i);
            cumulativeProb += logits.getFloat(indices[i]);
            if (cumulativeProb > topP) {
                lastIndex = i;
                break; // we've exceeded topp by including lastIndex
            }
            siftDown(indices, 0, i - 1, comparator);
        }

        // sample from the truncated list
        float r = rng.nextFloat(1f) * cumulativeProb;
        float cdf = 0.0f;
        for (int i = n0 - 1; i >= lastIndex; i--) {
            cdf += logits.getFloat(indices[i]);
            if (r < cdf) {
                return indices[i];
            }
        }

        return indices[lastIndex]; // in case of rounding errors
    }
}

