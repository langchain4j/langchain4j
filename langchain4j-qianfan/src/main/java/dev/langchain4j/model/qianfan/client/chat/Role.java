package dev.langchain4j.model.qianfan.client.chat;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(Role.RoleAdapter.class)
public enum Role {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    FUNCTION("function");

    private final String stringValue;

    private Role(String stringValue) {
        this.stringValue = stringValue;
    }

    public String toString() {
        return this.stringValue;
    }

    static Role from(String stringValue) {
        Role[] var1 = values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            Role role = var1[var3];
            if (role.stringValue.equals(stringValue)) {
                return role;
            }
        }

        throw new IllegalArgumentException("Unknown role: '" + stringValue + "'");
    }

    static class RoleAdapter extends TypeAdapter<Role> {
        RoleAdapter() {
        }

        public void write(JsonWriter jsonWriter, Role role) throws IOException {
            jsonWriter.value(role.toString());
        }

        public Role read(JsonReader jsonReader) throws IOException {
            return Role.from(jsonReader.nextString());
        }
    }
}

