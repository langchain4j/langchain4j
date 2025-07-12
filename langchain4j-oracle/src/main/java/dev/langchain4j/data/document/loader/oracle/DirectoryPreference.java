package dev.langchain4j.data.document.loader.oracle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Document loader directory preference
 *
 * To specify a directory, dbms_vector_chain.utl_to_text expects the following JSON:
 * {"dir": "directory name"}
 */
public class DirectoryPreference {
    private String dir;

    public DirectoryPreference() {}

    @JsonIgnore
    public boolean isValid() {
        return dir != null;
    }

    @JsonProperty("dir")
    public void setDirectory(String dir) {
        this.dir = dir;
    }

    @JsonProperty("dir")
    public String getDirectory() {
        return dir;
    }
}
