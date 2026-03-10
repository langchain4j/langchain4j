package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import jakarta.persistence.Embeddable;

@Embeddable
public class BookDetailsEmbeddable {
    @MetadataAttribute
    private String language;

    private String abstractText;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(final String language) {
        this.language = language;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(final String abstractText) {
        this.abstractText = abstractText;
    }
}
