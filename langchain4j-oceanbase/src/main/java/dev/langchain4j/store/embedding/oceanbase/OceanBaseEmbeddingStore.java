package dev.langchain4j.store.embedding.oceanbase;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.oceanbase.CollectionRequestBuilder.buildWhereExpression;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Represents an <a href="https://www.oceanbase.com/">OceanBase</a> database as an embedding store.
 * <br>
 * Supports storing {@link Metadata} and filtering by it using a {@link Filter}
 * (provided inside an {@link EmbeddingSearchRequest}).
 * <br>
 * Uses direct JDBC + SQL instead of obvec_jdbc SDK.
 */
public class OceanBaseEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(OceanBaseEmbeddingStore.class);

    private static final String DEFAULT_ID_FIELD_NAME = "id";
    private static final String DEFAULT_TEXT_FIELD_NAME = "text";
    private static final String DEFAULT_METADATA_FIELD_NAME = "metadata";
    private static final String DEFAULT_VECTOR_FIELD_NAME = "vector";
    private static final String DEFAULT_METRIC_TYPE = "cosine";

    private static final String DISTANCE_FUNCTION_COSINE = "cosine_distance";
    private static final String DISTANCE_FUNCTION_L2 = "l2_distance";
    private static final String DISTANCE_FUNCTION_INNER_PRODUCT = "inner_product";

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final String url;
    private final String user;
    private final String password;
    private final String tableName;
    private final String metricType;
    private final boolean retrieveEmbeddingsOnSearch;
    private final FieldDefinition fieldDefinition;
    private final boolean enableHybridSearch;
    private final int dimension;

    public OceanBaseEmbeddingStore(Builder builder) {
        this.url = ensureNotNull(builder.url, "url");
        this.user = ensureNotNull(builder.user, "user");
        this.password = ensureNotNull(builder.password, "password");
        this.tableName = getOrDefault(builder.tableName, "default");
        this.metricType = getOrDefault(builder.metricType, DEFAULT_METRIC_TYPE);
        this.retrieveEmbeddingsOnSearch = getOrDefault(builder.retrieveEmbeddingsOnSearch, false);
        this.fieldDefinition = new FieldDefinition(
                getOrDefault(builder.idFieldName, DEFAULT_ID_FIELD_NAME),
                getOrDefault(builder.textFieldName, DEFAULT_TEXT_FIELD_NAME),
                getOrDefault(builder.metadataFieldName, DEFAULT_METADATA_FIELD_NAME),
                getOrDefault(builder.vectorFieldName, DEFAULT_VECTOR_FIELD_NAME));
        this.enableHybridSearch = getOrDefault(builder.enableHybridSearch, false);
        this.dimension = ensureNotNull(builder.dimension, "dimension");

        initializeDatabase();
        
        if (enableHybridSearch) {
            ensureFulltextIndexExists();
        }
    }

    private void initializeDatabase() {
        if (!tableExists()) {
            createTable();
            createVectorIndex();
        }
    }

    private boolean tableExists() {
        String sql = "SHOW TABLES LIKE ?";
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check if table '{}' exists", tableName, e);
            throw new RequestToOceanBaseFailedException(
                    format("Failed to check if table '%s' exists", tableName), e);
        }
    }

    private void createTable() {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (");
        sql.append("`").append(fieldDefinition.getIdFieldName()).append("` VARCHAR(255) PRIMARY KEY, ");
        sql.append("`").append(fieldDefinition.getVectorFieldName()).append("` VECTOR(").append(dimension).append(") NOT NULL, ");
        sql.append("`").append(fieldDefinition.getTextFieldName()).append("` LONGTEXT, ");
        sql.append("`").append(fieldDefinition.getMetadataFieldName()).append("` JSON");
        sql.append(")");

        executeUpdate(sql.toString());
    }

    private void createVectorIndex() {
        String indexName = tableName.toLowerCase() + "_vidx";
        if (checkIndexExists(indexName) || hasVectorIndexOnColumn()) {
            return;
        }

        try {
            String distanceFunc = getDistanceFunction(metricType);
            String indexSql = format(
                    "CREATE VECTOR INDEX %s ON `%s` (`%s`) WITH (distance=%s, type=HNSW)",
                    indexName, tableName, fieldDefinition.getVectorFieldName(), distanceFunc);
            executeUpdate(indexSql);
        } catch (Exception e) {
            log.warn("Failed to create vector index '{}' on table '{}', continuing without index: {}", 
                    indexName, tableName, e.getMessage(), e);
        }
    }

    private boolean checkIndexExists(String indexName) {
        String sql = "SHOW INDEX FROM `" + tableName + "` WHERE Key_name = ?";
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, indexName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.debug("Failed to check if index '{}' exists on table '{}': {}", 
                    indexName, tableName, e.getMessage());
            return false;
        }
    }

    private boolean hasVectorIndexOnColumn() {
        String sql = "SHOW CREATE TABLE `" + tableName + "`";
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                String createTableSql = rs.getString(2);
                if (createTableSql != null) {
                    String lowerSql = createTableSql.toLowerCase();
                    return lowerSql.contains("vector index")
                            && lowerSql.contains(fieldDefinition.getVectorFieldName().toLowerCase());
                }
            }
        } catch (SQLException e) {
            log.debug("Failed to check vector index on table '{}': {}", tableName, e.getMessage());
        }
        return false;
    }

    /**
     * Gets the OceanBase distance function name for the given metric type.
     * 
     * Supported metrics:
     * - "cosine" (default): cosine_distance - Best for text embeddings, measures angular similarity [0, 2]
     * - "l2" or "euclidean": l2_distance - Measures Euclidean distance, considers magnitude [0, ∞)
     * - "inner_product" or "ip": inner_product - Dot product, fastest for normalized vectors [-∞, ∞]
     * 
     * @param metricType The metric type string
     * @return OceanBase distance function name
     */
    private String getDistanceFunction(String metricType) {
        if (metricType == null) {
            return DISTANCE_FUNCTION_COSINE;
        }
        switch (metricType.toLowerCase()) {
            case "l2":
            case "euclidean":
                return DISTANCE_FUNCTION_L2;
            case "cosine":
                return DISTANCE_FUNCTION_COSINE;
            case "inner_product":
            case "ip":
                return DISTANCE_FUNCTION_INNER_PRODUCT;
            default:
                return DISTANCE_FUNCTION_COSINE;
        }
    }

    private String getDistanceFunctionName(String metricType) {
        return getDistanceFunction(metricType);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void executeUpdate(String sql) {
        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            log.error("Failed to execute SQL: {}", sql, e);
            throw new RequestToOceanBaseFailedException(
                    format("Failed to execute SQL: %s", sql), e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public void dropCollection(String tableName) {
        String sql = "DROP TABLE IF EXISTS `" + tableName + "`";
        executeUpdate(sql);
    }

    @Override
    public String add(Embedding embedding) {
        String id = Utils.randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = Utils.randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            ids.add(Utils.randomUUID());
        }
        addAll(ids, embeddings, null);
        return ids;
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAll(singletonList(id), singletonList(embedding), 
                textSegment == null ? null : singletonList(textSegment));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            return;
        }

        String sql = format(
                "INSERT INTO `%s` (`%s`, `%s`, `%s`, `%s`) VALUES (?, ?, ?, ?)",
                tableName,
                fieldDefinition.getIdFieldName(),
                fieldDefinition.getVectorFieldName(),
                fieldDefinition.getTextFieldName(),
                fieldDefinition.getMetadataFieldName());

        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                Embedding embedding = embeddings.get(i);
                TextSegment textSegment = (textSegments != null && i < textSegments.size()) 
                        ? textSegments.get(i) : null;

                String vectorString = convertEmbeddingToString(embedding.vector());
                String text = textSegment != null ? textSegment.text() : "";
                String metadataJson = serializeMetadata(
                        textSegment != null ? textSegment.metadata() : null);

                pstmt.setString(1, id);
                pstmt.setString(2, vectorString);
                pstmt.setString(3, text);
                pstmt.setString(4, metadataJson);
                pstmt.addBatch();
            }
            
            pstmt.executeBatch();
        } catch (SQLException e) {
            log.error("Failed to insert {} embeddings into table '{}'", ids.size(), tableName, e);
            throw new RequestToOceanBaseFailedException(
                    format("Failed to insert embeddings into table '%s'", tableName), e);
        }
    }

    private String convertEmbeddingToString(float[] embedding) {
        return "[" + IntStream.range(0, embedding.length)
                .mapToObj(i -> String.valueOf(embedding[i]))
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "]";
    }

    private String serializeMetadata(Metadata metadata) {
        if (metadata == null) {
            return "{}";
        }
        Map<String, Object> metadataMap = metadata.toMap();
        if (metadataMap == null || metadataMap.isEmpty()) {
            return "{}";
        }
        Map<String, Object> processedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadataMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Long longValue) {
                if (longValue > 9007199254740991L || longValue < -9007199254740991L) {
                    processedMap.put(entry.getKey(), String.valueOf(longValue));
                } else {
                    processedMap.put(entry.getKey(), value);
                }
            } else {
                processedMap.put(entry.getKey(), value);
            }
        }
        return GSON.toJson(processedMap);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {
        if (enableHybridSearch && embeddingSearchRequest.query() != null 
                && !embeddingSearchRequest.query().trim().isEmpty()) {
            return hybridSearch(embeddingSearchRequest);
        }
        return vectorSimilaritySearch(embeddingSearchRequest);
    }

    private EmbeddingSearchResult<TextSegment> vectorSimilaritySearch(EmbeddingSearchRequest request) {
        List<Float> queryVector = request.queryEmbedding().vectorAsList();
        float[] vectorArray = new float[queryVector.size()];
        for (int i = 0; i < queryVector.size(); i++) {
            vectorArray[i] = queryVector.get(i);
        }
        String vectorString = convertEmbeddingToString(vectorArray);

        String distanceFunc = getDistanceFunctionName(metricType);
        String whereExpr = buildWhereExpression(request.filter(), fieldDefinition);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT `").append(fieldDefinition.getIdFieldName()).append("`, ");
        sql.append("`").append(fieldDefinition.getTextFieldName()).append("`, ");
        sql.append("`").append(fieldDefinition.getMetadataFieldName()).append("`");
        if (retrieveEmbeddingsOnSearch) {
            sql.append(", `").append(fieldDefinition.getVectorFieldName()).append("`");
        }
        String distanceExpr = distanceFunc + "(`" + fieldDefinition.getVectorFieldName() + "`, ?)";
        if ("cosine".equalsIgnoreCase(metricType)) {
            sql.append(", ").append(distanceExpr).append(" as distance");
            sql.append(", (").append("2 - ").append(distanceExpr).append(") / 2 as score");
        } else if ("l2".equalsIgnoreCase(metricType) || "euclidean".equalsIgnoreCase(metricType)) {
            sql.append(", ").append(distanceExpr).append(" as distance");
            sql.append(", 1 / (1 + ").append(distanceExpr).append(") as score");
        } else if ("inner_product".equalsIgnoreCase(metricType) || "ip".equalsIgnoreCase(metricType)) {
            sql.append(", ").append(distanceExpr).append(" as distance");
            sql.append(", (").append(distanceExpr).append(" + 1) / 2 as score");
        } else {
            sql.append(", ").append(distanceExpr).append(" as distance");
            sql.append(", (").append("2 - ").append(distanceExpr).append(") / 2 as score");
        }
        sql.append(" FROM `").append(tableName).append("`");

        if (whereExpr != null && !whereExpr.trim().isEmpty()) {
            sql.append(" WHERE ").append(whereExpr);
        }

        sql.append(" ORDER BY ").append(distanceFunc).append("(`")
                .append(fieldDefinition.getVectorFieldName()).append("`, ?) ASC ");

        boolean useApproximateLimit = checkIndexExists(tableName.toLowerCase() + "_vidx") 
                || hasVectorIndexOnColumn();
        if (useApproximateLimit) {
            sql.append("APPROXIMATE LIMIT ?");
        } else {
            sql.append("LIMIT ?");
        }
        
        String finalSql = sql.toString();
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(finalSql)) {
            
            pstmt.setString(1, vectorString);
            pstmt.setString(2, vectorString);
            pstmt.setString(3, vectorString);
            pstmt.setInt(4, request.maxResults());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    EmbeddingMatch<TextSegment> match = extractMatchFromResultSet(rs, request);
                    if (match != null && match.score() >= request.minScore()) {
                        matches.add(match);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to perform similarity search in table '{}'", tableName, e);
            throw new RequestToOceanBaseFailedException(
                    format("Failed to perform similarity search in table '%s'", tableName), e);
        }

        return new EmbeddingSearchResult<>(matches);
    }

    private EmbeddingSearchResult<TextSegment> hybridSearch(EmbeddingSearchRequest request) {
        // Perform vector similarity search
        EmbeddingSearchRequest vectorRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(request.queryEmbedding())
                .filter(request.filter())
                .maxResults(request.maxResults() * 2)
                .minScore(0.0)
                .build();
        List<EmbeddingMatch<TextSegment>> vectorMatches = vectorSimilaritySearch(vectorRequest).matches();
        List<EmbeddingMatch<TextSegment>> fulltextMatches = performFulltextSearch(
                request.query(),
                request.queryEmbedding(),
                request.filter(),
                request.maxResults() * 2);
        List<EmbeddingMatch<TextSegment>> combinedMatches = combineHybridResults(
                vectorMatches,
                fulltextMatches,
                request.maxResults());
        List<EmbeddingMatch<TextSegment>> result = combinedMatches.stream()
                .filter(match -> match.score() >= request.minScore())
                .collect(toList());

        return new EmbeddingSearchResult<>(result);
    }

    private List<EmbeddingMatch<TextSegment>> performFulltextSearch(
            String queryText, Embedding queryEmbedding, Filter filter, int maxResults) {
        String whereExpr = buildWhereExpression(filter, fieldDefinition);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT `").append(fieldDefinition.getIdFieldName()).append("`, ");
        sql.append("`").append(fieldDefinition.getTextFieldName()).append("`, ");
        sql.append("`").append(fieldDefinition.getMetadataFieldName()).append("`");
        if (retrieveEmbeddingsOnSearch) {
            sql.append(", `").append(fieldDefinition.getVectorFieldName()).append("`");
        }
        sql.append(", MATCH(`").append(fieldDefinition.getTextFieldName())
                .append("`) AGAINST(? IN NATURAL LANGUAGE MODE) as score ");
        sql.append("FROM `").append(tableName).append("` ");
        sql.append("WHERE MATCH(`").append(fieldDefinition.getTextFieldName())
                .append("`) AGAINST(? IN NATURAL LANGUAGE MODE)");

        if (whereExpr != null && !whereExpr.trim().isEmpty()) {
            sql.append(" AND ").append(whereExpr);
        }

        sql.append(" ORDER BY score DESC LIMIT ?");

        List<EmbeddingMatch<TextSegment>> results = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            
            pstmt.setString(1, queryText);
            pstmt.setString(2, queryText);
            pstmt.setInt(3, maxResults);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put(fieldDefinition.getIdFieldName(), rs.getString(fieldDefinition.getIdFieldName()));
                    row.put(fieldDefinition.getTextFieldName(), rs.getString(fieldDefinition.getTextFieldName()));
                    row.put(fieldDefinition.getMetadataFieldName(), rs.getString(fieldDefinition.getMetadataFieldName()));
                    double fulltextScore = rs.getDouble("score");
                    double normalizedScore = Math.min(1.0, fulltextScore / 10.0);
                    row.put("score", normalizedScore);
                    
                    EmbeddingMatch<TextSegment> match = toEmbeddingMatch(row, 0.0);
                    if (match != null) {
                        results.add(match);
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to perform fulltext search in table '{}', returning empty results: {}", 
                    tableName, e.getMessage(), e);
            return new ArrayList<>();
        }
        
        return results;
    }

    private List<EmbeddingMatch<TextSegment>> combineHybridResults(
            List<EmbeddingMatch<TextSegment>> vectorResults,
            List<EmbeddingMatch<TextSegment>> fulltextResults,
            int topK) {
        Map<String, EmbeddingMatch<TextSegment>> docMap = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();
        
        final int k = 60;

        for (int i = 0; i < vectorResults.size(); i++) {
            EmbeddingMatch<TextSegment> match = vectorResults.get(i);
            String id = match.embeddingId();
            docMap.put(id, match);
            int rank = i + 1;
            rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (k + rank));
        }

        for (int i = 0; i < fulltextResults.size(); i++) {
            EmbeddingMatch<TextSegment> match = fulltextResults.get(i);
            String id = match.embeddingId();
            if (!docMap.containsKey(id)) {
                docMap.put(id, match);
            }
            int rank = i + 1;
            rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (k + rank));
        }

        double maxRrfScore = rrfScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0);
        return docMap.values().stream()
                .sorted((d1, d2) -> Double.compare(
                        rrfScores.getOrDefault(d2.embeddingId(), 0.0),
                        rrfScores.getOrDefault(d1.embeddingId(), 0.0)
                ))
                .limit(topK)
                .map(match -> {
                    double normalizedScore = maxRrfScore > 0 
                            ? rrfScores.getOrDefault(match.embeddingId(), 0.0) / maxRrfScore
                            : 0.0;
                    return new EmbeddingMatch<>(
                            normalizedScore,
                            match.embeddingId(),
                            match.embedding(),
                            match.embedded()
                    );
                })
                .collect(toList());
    }

    private void ensureFulltextIndexExists() {
        String indexName = tableName.toLowerCase() + "_fts_idx";
        if (checkIndexExists(indexName)) {
            return;
        }

        try {
            String indexSql = format(
                    "CREATE FULLTEXT INDEX %s ON `%s` (`%s`) WITH PARSER ngram",
                    indexName, tableName, fieldDefinition.getTextFieldName());
            executeUpdate(indexSql);
        } catch (Exception e) {
            log.warn("Failed to create fulltext index '{}' on table '{}', continuing without index: {}", 
                    indexName, tableName, e.getMessage(), e);
        }
    }

    private EmbeddingMatch<TextSegment> extractMatchFromResultSet(
            ResultSet rs, EmbeddingSearchRequest request) throws SQLException {
        String id = rs.getString(fieldDefinition.getIdFieldName());
        String text = rs.getString(fieldDefinition.getTextFieldName());
        String metadataJson = rs.getString(fieldDefinition.getMetadataFieldName());
        
        double score = 0.0;
        try {
            score = rs.getDouble("score");
        } catch (SQLException e) {
            try {
                String scoreStr = rs.getString("score");
                if (scoreStr != null) {
                    score = Double.parseDouble(scoreStr);
                }
            } catch (Exception ex) {
                try {
                    double distance = rs.getDouble("distance");
                    if ("cosine".equalsIgnoreCase(metricType)) {
                        score = (2.0 - distance) / 2.0;
                    } else {
                        score = distance;
                    }
                } catch (SQLException ex2) {
                    log.debug("Failed to extract score from result set, using default 0.0: {}", ex2.getMessage());
                }
            }
        }

        Embedding embedding = null;
        if (retrieveEmbeddingsOnSearch) {
            String vectorStr = rs.getString(fieldDefinition.getVectorFieldName());
            if (vectorStr != null) {
                float[] vector = parseVectorFromString(vectorStr);
                if (vector.length > 0) {
                    embedding = new Embedding(vector);
                }
            }
        }

        Metadata metadata = parseMetadata(metadataJson);
        TextSegment textSegment = text != null && !text.isEmpty()
                ? TextSegment.from(text, metadata)
                : null;

        return new EmbeddingMatch<>(score, id, embedding, textSegment);
    }

    private float[] parseVectorFromString(String vectorStr) {
        try {
            String cleaned = vectorStr.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            String[] parts = cleaned.split(",");
            float[] vector = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
            return vector;
        } catch (Exception e) {
            log.warn("Failed to parse vector from string, returning empty vector: {}", e.getMessage(), e);
            return new float[0];
        }
    }

    private Metadata parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.trim().isEmpty() || metadataJson.equals("{}")) {
            return Metadata.from(new HashMap<>());
        }
        try {
            Map<String, Object> metadataMap = GSON.fromJson(metadataJson, MAP_TYPE);
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : metadataMap.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String stringValue) {
                    try {
                        long longValue = Long.parseLong(stringValue);
                        result.put(entry.getKey(), longValue);
                        continue;
                    } catch (NumberFormatException e) {
                        log.debug("Metadata value '{}' is not a valid Long, keeping as string: {}", 
                                entry.getKey(), e.getMessage());
                        result.put(entry.getKey(), value);
                        continue;
                    }
                }
                if (value instanceof BigDecimal) {
                    BigDecimal bd = (BigDecimal) value;
                    if (bd.scale() == 0) {
                        try {
                            long longValue = bd.longValueExact();
                            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                                result.put(entry.getKey(), (int) longValue);
                            } else {
                                result.put(entry.getKey(), longValue);
                            }
                        } catch (ArithmeticException e) {
                            log.debug("Metadata BigDecimal value '{}' is too large for Long, using double: {}", 
                                    entry.getKey(), e.getMessage());
                            result.put(entry.getKey(), bd.doubleValue());
                        }
                    } else {
                        result.put(entry.getKey(), bd.doubleValue());
                    }
                } else {
                    result.put(entry.getKey(), value);
                }
            }
            return Metadata.from(result);
        } catch (Exception e) {
            log.warn("Failed to parse metadata JSON, returning empty metadata: {}", e.getMessage(), e);
            return Metadata.from(new HashMap<>());
        }
    }

    private EmbeddingMatch<TextSegment> toEmbeddingMatch(Map<String, Object> row, double defaultScore) {
        String id = (String) row.get(fieldDefinition.getIdFieldName());
        String text = (String) row.get(fieldDefinition.getTextFieldName());
        String metadataJson = (String) row.get(fieldDefinition.getMetadataFieldName());
        Object scoreObj = row.get("score");
        
        double score = defaultScore;
        if (scoreObj != null) {
            if (scoreObj instanceof Number) {
                score = ((Number) scoreObj).doubleValue();
            } else {
                try {
                    score = Double.parseDouble(scoreObj.toString());
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse score value '{}', using default score: {}", 
                            scoreObj, e.getMessage());
                }
            }
        }

        Metadata metadata = parseMetadata(metadataJson);
        TextSegment textSegment = text != null && !text.isEmpty()
                ? TextSegment.from(text, metadata)
                : null;

        return new EmbeddingMatch<>(score, id, null, textSegment);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        String sql = format("DELETE FROM `%s` WHERE `%s` = ?",
                tableName, fieldDefinition.getIdFieldName());
        
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (String id : ids) {
                pstmt.setString(1, id);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            log.error("Failed to delete {} embeddings from table '{}'", ids.size(), tableName, e);
            throw new RequestToOceanBaseFailedException(
                    format("Failed to delete embeddings from table '%s'", tableName), e);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        String whereExpr = buildWhereExpression(filter, fieldDefinition);
        if (whereExpr == null || whereExpr.trim().isEmpty()) {
            throw new UnsupportedOperationException(
                    "removeAll(Filter) requires a valid filter expression");
        }
        String sql = format("DELETE FROM `%s` WHERE %s", tableName, whereExpr);
        executeUpdate(sql);
    }

    @Override
    public void removeAll() {
        String sql = format("DELETE FROM `%s`", tableName);
        executeUpdate(sql);
    }

    public static class Builder {

        private String url;
        private String user;
        private String password;
        private String tableName;
        private Integer dimension;
        private String metricType;
        private Boolean retrieveEmbeddingsOnSearch;
        private String idFieldName;
        private String textFieldName;
        private String metadataFieldName;
        private String vectorFieldName;
        private Boolean enableHybridSearch;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder metricType(String metricType) {
            this.metricType = metricType;
            return this;
        }

        public Builder retrieveEmbeddingsOnSearch(Boolean retrieveEmbeddingsOnSearch) {
            this.retrieveEmbeddingsOnSearch = retrieveEmbeddingsOnSearch;
            return this;
        }

        public Builder idFieldName(String idFieldName) {
            this.idFieldName = idFieldName;
            return this;
        }

        public Builder textFieldName(String textFieldName) {
            this.textFieldName = textFieldName;
            return this;
        }

        public Builder metadataFieldName(String metadataFieldName) {
            this.metadataFieldName = metadataFieldName;
            return this;
        }

        public Builder vectorFieldName(String vectorFieldName) {
            this.vectorFieldName = vectorFieldName;
            return this;
        }

        public Builder enableHybridSearch(Boolean enableHybridSearch) {
            this.enableHybridSearch = enableHybridSearch;
            return this;
        }

        public OceanBaseEmbeddingStore build() {
            return new OceanBaseEmbeddingStore(this);
        }
    }
}
