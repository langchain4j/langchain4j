package dev.langchain4j.data.document.loader.oracle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Document loader file preference
 *
 * To specify a table, dbms_vector_chain.utl_to_text expects the following JSON:
 * {"owner": "owner", "tablename": "table name", "colname": "column name"}
 */
public class TablePreference {
    private String owner;
    private String tablename;
    private String colname;

    public TablePreference() {}

    @JsonIgnore
    public boolean isValid() {
        return owner != null && tablename != null && colname != null;
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
