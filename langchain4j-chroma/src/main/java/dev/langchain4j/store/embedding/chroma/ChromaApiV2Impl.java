package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Internal
class ChromaApiV2Impl {

    private final ChromaHttpClient httpClient;

    public ChromaApiV2Impl(ChromaHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void createTenant(Tenant tenant) throws IOException {
        httpClient.post("api/v2/tenants", tenant, Void.class);
    }

    public Tenant tenant(String tenantName) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("tenant_name", tenantName);
        return httpClient.get("api/v2/tenants/{tenant_name}", Tenant.class, pathParams);
    }

    public void createDatabase(String tenantName, Database createDatabaseRequest) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("tenant_name", tenantName);
        httpClient.post("api/v2/tenants/{tenant_name}/databases", createDatabaseRequest, Void.class, pathParams);
    }

    public Database database(String tenantName, String databaseName) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("tenant_name", tenantName);
        pathParams.put("database_name", databaseName);
        return httpClient.get("api/v2/tenants/{tenant_name}/databases/{database_name}", Database.class, pathParams);
    }

    public Collection collection(String tenantName, String databaseName, String collectionName) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("tenant_name", tenantName);
        pathParams.put("database_name", databaseName);
        pathParams.put("collection_name", collectionName);
        return httpClient.get(
                "api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_name}",
                Collection.class,
                pathParams);
    }

    public Collection createCollection(
            String tenantName, String databaseName, CreateCollectionRequest createCollectionRequest)
            throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("tenant_name", tenantName);
        pathParams.put("database_name", databaseName);
        return httpClient.post(
                "api/v2/tenants/{tenant_name}/databases/{database_name}/collections",
                createCollectionRequest,
                Collection.class,
                pathParams);
    }

    public void addEmbeddings(
            String tenantName, String databaseName, String collectionId, AddEmbeddingsRequest embedding)
            throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("tenant_name", tenantName);
        pathParams.put("database_name", databaseName);
        pathParams.put("collection_id", collectionId);
        httpClient.post(
                "api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_id}/add",
                embedding,
                Void.class,
                pathParams);
    }

    public QueryResponse queryCollection(
            String tenantName, String databaseName, String collectionId, QueryRequest queryRequest) throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("tenant_name", tenantName);
        pathParams.put("database_name", databaseName);
        pathParams.put("collection_id", collectionId);
        return httpClient.post(
                "api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_id}/query",
                queryRequest,
                QueryResponse.class,
                pathParams);
    }

    @SuppressWarnings("unchecked")
    public void deleteEmbeddings(
            String tenantName, String databaseName, String collectionId, DeleteEmbeddingsRequest embedding)
            throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("tenant_name", tenantName);
        pathParams.put("database_name", databaseName);
        pathParams.put("collection_id", collectionId);
        httpClient.post(
                "api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_id}/delete",
                embedding,
                Void.class,
                pathParams);
    }

    public Collection deleteCollection(String tenantName, String databaseName, String collectionName)
            throws IOException {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("tenant_name", tenantName);
        pathParams.put("database_name", databaseName);
        pathParams.put("collection_name", collectionName);
        httpClient.delete(
                "api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_name}", pathParams);
        return null; // DELETE operations typically don't return a body
    }
}
