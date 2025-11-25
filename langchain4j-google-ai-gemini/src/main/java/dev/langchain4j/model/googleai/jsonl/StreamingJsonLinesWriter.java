package dev.langchain4j.model.googleai.jsonl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class StreamingJsonLinesWriter implements JsonLinesWriter {
    private final BufferedWriter writer;
    private final ObjectMapper objectMapper;

    StreamingJsonLinesWriter(Path path) throws IOException {
        this(Files.newBufferedWriter(path, StandardCharsets.UTF_8), new ObjectMapper());
    }

    StreamingJsonLinesWriter(OutputStream outputStream) {
        this(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), new ObjectMapper());
    }

    StreamingJsonLinesWriter(Writer writer, ObjectMapper objectMapper) {
        this.writer = writer instanceof BufferedWriter bufferedWriter ? bufferedWriter : new BufferedWriter(writer);
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(Object object) throws IOException {
        objectMapper.writer().without(JsonGenerator.Feature.AUTO_CLOSE_TARGET).writeValue(writer, object);
        writer.newLine();
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
