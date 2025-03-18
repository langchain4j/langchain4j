package dev.langchain4j.engine;

/**
 * Metadata column information used to define a new table colunm
 */
public class MetadataColumn {

    private final String name;
    private final String type;
    private final Boolean nullable;

    /**
     * Metadata column's name, type and nullable constraint
     * @param name the column name
     * @param type supported types: "text", "char()", "varchar()", "uuid", "integer", "bigint", "real" and "double"
     * @param nullable should use nullable constraint
     */
    public MetadataColumn(String name, String type, Boolean nullable) {
        this.name = name;
        this.type = type;
        this.nullable = nullable;
    }

    /**
     * generate the column clause to be used by {@link AlloyDBEngine}
     * @return column clause for create table
     */
    public String generateColumnString() {
        return String.format("\"%s\" %s %s", name, type, nullable ? "" : "NOT NULL");
    }

    /**
     * the metadata column name
     * @return name string
     */
    public String getName() {
        return name;
    }
}
