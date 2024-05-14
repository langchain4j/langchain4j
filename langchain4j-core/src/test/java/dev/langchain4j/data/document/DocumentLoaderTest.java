package dev.langchain4j.data.document;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;


class DocumentLoaderTest implements WithAssertions {
    public static final class StringSource implements DocumentSource {
        private final String content;
        private final Metadata metadata;

        public StringSource(String content, Metadata metadata) {
            this.content = content;
            this.metadata = metadata;
        }

        @Override
        public InputStream inputStream() {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Metadata metadata() {
            return metadata;
        }
    }

    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        final int bufLen = 4 * 0x400; // 4KB
        byte[] buf = new byte[bufLen];
        int readLen;
        IOException exception = null;

        try {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                while ((readLen = inputStream.read(buf, 0, bufLen)) != -1)
                    outputStream.write(buf, 0, readLen);

                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            exception = e;
            throw e;
        } finally {
            if (exception == null) inputStream.close();
            else try {
                inputStream.close();
            } catch (IOException e) {
                exception.addSuppressed(e);
            }
        }
    }

    public static final class TrivialParser implements DocumentParser {
        @Override
        public Document parse(InputStream inputStream) {
            String str;
            try {
                str = new String(readAllBytes(inputStream), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read input stream", e);
            }

            return Document.from(str);
        }
    }

    @Test
    public void test_load() {
        StringSource source = new StringSource("Hello, world!", new Metadata().add("foo", "bar"));
        Document document = DocumentLoader.load(source, new TrivialParser());
        assertThat(document).isEqualTo(Document.from("Hello, world!", new Metadata().add("foo", "bar")));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> DocumentLoader.load(new DocumentSource() {
                    @Override
                    public InputStream inputStream() throws IOException {
                        throw new IOException("Failed to open input stream");
                    }

                    @Override
                    public Metadata metadata() {
                        return new Metadata();
                    }
                }, new TrivialParser()))
                .withMessageContaining("Failed to load document");

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> DocumentLoader.load(
                        source,
                        inputStream -> {
                            throw new RuntimeException("Failed to parse document");
                        }

                ))
                .withMessageContaining("Failed to load document");
    }
}