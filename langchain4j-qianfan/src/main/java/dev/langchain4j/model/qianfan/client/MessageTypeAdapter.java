package dev.langchain4j.model.qianfan.client;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.langchain4j.model.qianfan.client.chat.FunctionCall;
import dev.langchain4j.model.qianfan.client.chat.Message;

import java.io.IOException;
public class MessageTypeAdapter extends TypeAdapter<Message> {
   public static final TypeAdapterFactory MESSAGE_TYPE_ADAPTER_FACTORY = new TypeAdapterFactory() {
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != Message.class) {
                return null;
            } else {
                TypeAdapter<Message> delegate = (TypeAdapter<Message>) gson.getDelegateAdapter(this, type);
                return (TypeAdapter<T>) new MessageTypeAdapter(delegate);
            }
        }
    };
    private final TypeAdapter<Message> delegate;

    private MessageTypeAdapter(TypeAdapter<Message> delegate) {
        this.delegate = delegate;
    }

    public void write(JsonWriter out, Message message) throws IOException {
        out.beginObject();
        out.name("role");
        out.value(message.role().toString());
        out.name("content");
        if (message.content() == null) {
            boolean serializeNulls = out.getSerializeNulls();
            out.setSerializeNulls(true);
            out.nullValue();
            out.setSerializeNulls(serializeNulls);
        } else {
            out.value(message.content());
        }

        if (message.name() != null) {
            out.name("name");
            out.value(message.name());
        }

        if (message.functionCall() != null) {
            out.name("function_call");
            TypeAdapter<FunctionCall> functionCallTypeAdapter = Json.GSON.getAdapter(
                    FunctionCall.class);
            functionCallTypeAdapter.write(out, message.functionCall());
        }

        out.endObject();
    }

    public Message read(JsonReader in) throws IOException {
        return (Message)this.delegate.read(in);
    }
}
