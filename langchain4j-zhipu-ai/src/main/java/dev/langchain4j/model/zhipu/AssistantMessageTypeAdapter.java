package dev.langchain4j.model.zhipu;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.langchain4j.model.zhipu.chat.AssistantMessage;
import dev.langchain4j.model.zhipu.chat.ToolCall;

import java.io.IOException;
import java.util.List;

class AssistantMessageTypeAdapter extends TypeAdapter<AssistantMessage> {

    static final TypeAdapterFactory ASSISTANT_MESSAGE_TYPE_ADAPTER_FACTORY = new TypeAdapterFactory() {

        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != AssistantMessage.class) {
                return null;
            }
            TypeAdapter<AssistantMessage> delegate =
                    (TypeAdapter<AssistantMessage>) gson.getDelegateAdapter(this, type);
            return (TypeAdapter<T>) new AssistantMessageTypeAdapter(delegate);
        }
    };

    private final TypeAdapter<AssistantMessage> delegate;

    private AssistantMessageTypeAdapter(TypeAdapter<AssistantMessage> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(JsonWriter out, AssistantMessage assistantMessage) throws IOException {
        out.beginObject();

        out.name("role");
        out.value(assistantMessage.getRole().toString().toLowerCase());

        out.name("content");
        if (assistantMessage.getContent() == null) {
            boolean serializeNulls = out.getSerializeNulls();
            out.setSerializeNulls(true);
            out.nullValue(); // serialize "content": null
            out.setSerializeNulls(serializeNulls);
        } else {
            out.value(assistantMessage.getContent());
        }

        if (assistantMessage.getName() != null) {
            out.name("name");
            out.value(assistantMessage.getName());
        }

        List<ToolCall> toolCalls = assistantMessage.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            out.name("tool_calls");
            out.beginArray();
            TypeAdapter<ToolCall> toolCallTypeAdapter = Json.GSON.getAdapter(ToolCall.class);
            for (ToolCall toolCall : toolCalls) {
                toolCallTypeAdapter.write(out, toolCall);
            }
            out.endArray();
        }

        out.endObject();
    }

    @Override
    public AssistantMessage read(JsonReader in) throws IOException {
        return delegate.read(in);
    }
}