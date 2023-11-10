package dev.langchain4j.spi.json;

import dev.langchain4j.internal.Json;

public interface JsonCodecFactory {

    Json.JsonCodec create();
}
