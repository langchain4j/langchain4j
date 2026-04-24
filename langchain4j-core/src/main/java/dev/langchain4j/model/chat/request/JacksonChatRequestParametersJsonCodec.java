package dev.langchain4j.model.chat.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.Internal;

@Internal
public class JacksonChatRequestParametersJsonCodec implements ChatRequestParametersJsonCodec {

    public static JsonMapper.Builder chatRequestParametersJsonMapperBuilder() {
        SimpleModule simpleModule = new SimpleModule("ChatRequestParametersModule")
                .addDeserializer(ChatRequestParameters.class, new ChatRequestParametersDeserializerImpl());

        return JsonMapper.builder().addModule(simpleModule);
    }

    private static final ObjectMapper OBJECT_MAPPER =
            chatRequestParametersJsonMapperBuilder().build();

    @Override
    public ChatRequestParameters chatParametersFromJson(final String json) {
        try {
            return OBJECT_MAPPER.readValue(json, ChatRequestParameters.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
