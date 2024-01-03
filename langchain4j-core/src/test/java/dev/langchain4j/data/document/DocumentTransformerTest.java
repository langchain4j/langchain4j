package dev.langchain4j.data.document;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class DocumentTransformerTest implements WithAssertions {
    @Test
    public void test() {
        List<Document> docs = new ArrayList<>();
        docs.add(Document.document("abc xyz", Metadata.metadata("lang", "en")));
        docs.add(Document.document("jkl 123", Metadata.metadata("lang", "en")));
        docs.add(Document.document("mno qrs", Metadata.metadata("lang", "fr")));

        List<Document> results = ((DocumentTransformer) document -> {
            if (document.metadata().get("lang").equals("en")) {
                return Document.document(
                        document.text().toUpperCase(Locale.ROOT),
                        document.metadata());
            } else {
                return null;
            }
        }).transformAll(docs);

        assertThat(results).containsOnly(
                Document.document("ABC XYZ", Metadata.metadata("lang", "en")),
                Document.document("JKL 123", Metadata.metadata("lang", "en"))
        );
    }

}