package dev.langchain4j.model.chat.request;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import dev.langchain4j.Internal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Internal
class ChatRequestParametersDeserializerImpl extends StdDeserializer<ChatRequestParameters> {

    public ChatRequestParametersDeserializerImpl() {
        super(ChatRequestParameters.class);
    }

    @Override
    public ChatRequestParameters deserialize(
            final JsonParser jsonParser, final DeserializationContext deserializationContext)
            throws IOException, JacksonException {

        JsonNode root = jsonParser.getCodec().readTree(jsonParser);
        DefaultChatRequestParameters.Builder<?> builder = DefaultChatRequestParameters.builder();

        String modelName = textOrNull(root, "model_name", "model");
        if (modelName != null) builder.modelName(modelName);

        if (root.hasNonNull("temperature")) {
            builder.temperature(root.get("temperature").doubleValue());
        }

        JsonNode topPNode = firstNonNull(root, "top_p", "topP");
        if (topPNode != null) builder.topP(topPNode.doubleValue());

        JsonNode topKNode = firstNonNull(root, "top_k", "topK");
        if (topKNode != null) builder.topK(topKNode.intValue());

        JsonNode freqNode = firstNonNull(root, "frequency_penalty", "frequencyPenalty");
        if (freqNode != null) builder.frequencyPenalty(freqNode.doubleValue());

        JsonNode presNode = firstNonNull(root, "presence_penalty", "presencePenalty");
        if (presNode != null) builder.presencePenalty(presNode.doubleValue());

        JsonNode maxTokNode = firstNonNull(root, "max_tokens", "max_output_tokens", "maxOutputTokens");
        if (maxTokNode != null) builder.maxOutputTokens(maxTokNode.intValue());

        JsonNode stopNode = firstNonNull(root, "stop", "stop_sequences", "stopSequences");
        if (stopNode != null) {
            List<String> stopSequences = new ArrayList<>();
            if (stopNode.isArray()) {
                stopNode.forEach(n -> stopSequences.add(n.asText()));
            } else if (stopNode.isTextual()) {
                stopSequences.add(stopNode.asText());
            }
            builder.stopSequences(stopSequences);
        }

        return builder.build();
    }

    private static String textOrNull(JsonNode root, String... fieldNames) {
        for (String name : fieldNames) {
            if (root.hasNonNull(name)) return root.get(name).asText();
        }
        return null;
    }

    private static JsonNode firstNonNull(JsonNode root, String... fieldNames) {
        for (String name : fieldNames) {
            if (root.hasNonNull(name)) return root.get(name);
        }
        return null;
    }
}
