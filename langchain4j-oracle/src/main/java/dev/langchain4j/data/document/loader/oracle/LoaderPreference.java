package dev.langchain4j.data.document.loader.oracle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Document loader preference
 *
 * The preference should be specified in the following format depending on
 * whether a file, directory, or a table of documents is desired
 *
 * for a file:      {"file": "filename"}
 * for a directory: {"dir": "directory name"}
 * for a table:     {"owner": "owner", "tablename": "table name", "colname": "column name"}
 */
public class LoaderPreference {

    private String file;
    private String dir;
    private String owner;
    private String tablename;
    private String colname;

    public LoaderPreference() {}

    @JsonProperty("file")
    public void setFile(String file) {
        this.file = file;
    }

    @JsonProperty("dir")
    public void setDirectory(String dir) {
        this.dir = dir;
    }

    @JsonProperty("owner")
    public void setOwner(String owner) {
        this.owner = owner;
    }

    @JsonProperty("tablename")
    public void setTableName(String tablename) {
        this.tablename = tablename;
    }

    @JsonProperty("colname")
    public void setColumnName(String colname) {
        this.colname = colname;
    }

    @JsonProperty("file")
    public String getFile() {
        return file;
    }

    @JsonProperty("dir")
    public String getDirectory() {
        return dir;
    }

    @JsonProperty("owner")
    public String getOwner() {
        return owner;
    }

    @JsonProperty("tablename")
    public String getTableName() {
        return tablename;
    }

    @JsonProperty("colname")
    public String getColumnName() {
        return colname;
    }
}
