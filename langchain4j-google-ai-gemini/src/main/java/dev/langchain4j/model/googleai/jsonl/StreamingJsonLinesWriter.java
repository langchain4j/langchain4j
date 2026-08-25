package dev.langchain4j.model.googleai.jsonl;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class StreamingJsonLinesWriter implements JsonLinesWriter {

    private static final Json.JsonCodec CODEC = WireJson.codec(WireJsonSpec.builder().build());

    private final BufferedWriter writer;

    StreamingJsonLinesWriter(Path path) throws IOException {
        this(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
    }

    StreamingJsonLinesWriter(OutputStream outputStream) {
        this(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    StreamingJsonLinesWriter(Writer writer) {
        this.writer = writer instanceof BufferedWriter bufferedWriter ? bufferedWriter : new BufferedWriter(writer);
    }

    @Override
    public void write(Object object) throws IOException {
        // Written as a string rather than straight to the writer, so that closing it stays this class's job.
        writer.write(CODEC.toJson(object));
        writer.newLine();
        // Jackson flushed after every value it wrote; keep each line reaching the target as it is written.
        writer.flush();
    }

    @Override
    public void write(Iterable<?> objects) throws IOException {
        for (Object object : objects) {
            write(object);
        }
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
