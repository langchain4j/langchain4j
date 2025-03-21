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
    private String file;

    public FilePreference() {}

    @JsonIgnore
    public boolean isValid() {
        return file != null;
    }

    @JsonProperty("file")
    public void setFile(String file) {
        this.file = file;
    }

    @JsonProperty("file")
    public String getFile() {
        return file;
    }
}
