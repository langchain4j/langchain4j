package dev.langchain4j.data.document.loader.oracle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Document loader file preference
 *
 * To specify a file, dbms_vector_chain.utl_to_text expects the following JSON:
 * {"file": "filename"}
 */
public class FilePreference {
    private String filename;

    public FilePreference() {}

    @JsonIgnore
    public boolean isValid() {
        return filename != null;
    }

    @JsonProperty("file")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @JsonProperty("file")
    public String getFilename() {
        return filename;
    }
}
