package io.pinecone.clients;

import io.pinecone.configs.PineconeConfig;
import io.pinecone.configs.PineconeConnection;
import io.pinecone.exceptions.PineconeException;
import io.pinecone.exceptions.PineconeValidationException;
import org.openapitools.db_control.client.model.CollectionList;
import org.openapitools.db_control.client.model.CollectionModel;
import org.openapitools.db_control.client.model.DeletionProtection;
import org.openapitools.db_control.client.model.IndexList;
import org.openapitools.db_control.client.model.IndexModel;
import org.openapitools.db_control.client.model.PodSpecMetadataConfig;

import java.util.concurrent.ConcurrentHashMap;

public class DockerPinecone extends Pinecone {

    private final Pinecone pinecone;
    private final Integer indexPort;

    public DockerPinecone(Pinecone pinecone, Integer indexPort) {
        super(pinecone.getConfig(), null);
        this.pinecone = pinecone;
        this.indexPort = indexPort;
    }

    @Override
    ConcurrentHashMap<String, PineconeConnection> getConnectionsMap() {
        return pinecone.getConnectionsMap();
    }

    @Override
    PineconeConfig getConfig() {
        return pinecone.getConfig();
    }

    @Override
    public IndexModel createServerlessIndex(final String indexName, final String metric, final int dimension, final String cloud, final String region, final DeletionProtection deletionProtection) throws PineconeException {
        return pinecone.createServerlessIndex(indexName, metric, dimension, cloud, region, deletionProtection);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType) {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType, final DeletionProtection deletionProtection) {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType, deletionProtection);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType, final String metric) {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType, metric);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType, final String metric, final PodSpecMetadataConfig metadataConfig) {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType, metric, metadataConfig);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType, final String metric, final String sourceCollection) {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType, metric, sourceCollection);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType, final Integer pods) {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType, pods);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType, final Integer pods, final PodSpecMetadataConfig metadataConfig) {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType, pods, metadataConfig);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType, final Integer replicas, final Integer shards) {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType, replicas, shards);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType, final Integer replicas, final Integer shards, final PodSpecMetadataConfig metadataConfig) {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType, replicas, shards, metadataConfig);
    }

    @Override
    public IndexModel createPodsIndex(final String indexName, final Integer dimension, final String environment, final String podType, final String metric, final Integer replicas, final Integer shards, final Integer pods, final PodSpecMetadataConfig metadataConfig, final String sourceCollection, final DeletionProtection deletionProtection) throws PineconeException {
        return pinecone.createPodsIndex(indexName, dimension, environment, podType, metric, replicas, shards, pods, metadataConfig, sourceCollection, deletionProtection);
    }

    @Override
    public IndexModel describeIndex(final String indexName) throws PineconeException {
        return pinecone.describeIndex(indexName);
    }

    @Override
    public IndexModel configurePodsIndex(final String indexName, final String podType, final Integer replicas, final DeletionProtection deletionProtection) throws PineconeException {
        return pinecone.configurePodsIndex(indexName, podType, replicas, deletionProtection);
    }

    @Override
    public IndexModel configurePodsIndex(final String indexName, final Integer replicas, final DeletionProtection deletionProtection) throws PineconeException {
        return pinecone.configurePodsIndex(indexName, replicas, deletionProtection);
    }

    @Override
    public IndexModel configurePodsIndex(final String indexName, final String podType) throws PineconeException {
        return pinecone.configurePodsIndex(indexName, podType);
    }

    @Override
    public IndexModel configurePodsIndex(final String indexName, final DeletionProtection deletionProtection) throws PineconeException {
        return pinecone.configurePodsIndex(indexName, deletionProtection);
    }

    @Override
    public IndexModel configureServerlessIndex(final String indexName, final DeletionProtection deletionProtection) throws PineconeException {
        return pinecone.configureServerlessIndex(indexName, deletionProtection);
    }

    @Override
    public IndexList listIndexes() throws PineconeException {
        return pinecone.listIndexes();
    }

    @Override
    public void deleteIndex(final String indexName) throws PineconeException {
        pinecone.deleteIndex(indexName);
    }

    @Override
    public CollectionModel createCollection(final String collectionName, final String sourceIndex) throws PineconeException {
        return pinecone.createCollection(collectionName, sourceIndex);
    }

    @Override
    public CollectionModel describeCollection(final String collectionName) throws PineconeException {
        return pinecone.describeCollection(collectionName);
    }

    @Override
    public CollectionList listCollections() throws PineconeException {
        return pinecone.listCollections();
    }

    @Override
    public void deleteCollection(final String collectionName) throws PineconeException {
        pinecone.deleteCollection(collectionName);
    }

    @Override
    public Index getIndexConnection(final String indexName) throws PineconeValidationException {
        if(indexName == null || indexName.isEmpty()) {
            throw new PineconeValidationException("Index name cannot be null or empty");
        }

        String dockerSelfHost = getIndexHost(indexName);
        if (dockerSelfHost == null || dockerSelfHost.isEmpty()) {
            throw new PineconeValidationException("Index host cannot be null or empty");
        }

        // replace by the docker mapped port
        String mappedHost = dockerSelfHost.replaceAll(":\\d+", ":" + indexPort);
        getConfig().setHost(mappedHost);
        PineconeConnection connection = getConnection(indexName);
        return new Index(connection, indexName);
    }

    @Override
    public AsyncIndex getAsyncIndexConnection(final String indexName) throws PineconeValidationException {
        return pinecone.getAsyncIndexConnection(indexName);
    }

    @Override
    public Inference getInferenceClient() {
        return pinecone.getInferenceClient();
    }

    @Override
    PineconeConnection getConnection(final String indexName) {
        return pinecone.getConnection(indexName);
    }

    @Override
    String getIndexHost(final String indexName) {
        return pinecone.getIndexHost(indexName);
    }
}
