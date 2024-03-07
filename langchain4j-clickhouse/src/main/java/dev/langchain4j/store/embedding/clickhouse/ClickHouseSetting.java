package dev.langchain4j.store.embedding.clickhouse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickHouseSetting {

    private String endpoint;
    private String username;
    private String password;
    private String database;
    private String table;
    private List<String> columnNames;
}
