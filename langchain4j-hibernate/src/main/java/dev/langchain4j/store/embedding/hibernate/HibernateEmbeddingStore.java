package dev.langchain4j.store.embedding.hibernate;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.Utils.toStringValueMap;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import java.io.StringReader;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.Generator;
import org.hibernate.internal.util.ReaderInputStream;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.spi.EntityInstantiator;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.SelectionQuery;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaConflictClause;
import org.hibernate.query.criteria.JpaConflictUpdateAction;
import org.hibernate.query.criteria.JpaCriteriaDelete;
import org.hibernate.query.criteria.JpaCriteriaInsertValues;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.criteria.JpaParameterExpression;
import org.hibernate.query.criteria.JpaPath;
import org.hibernate.query.criteria.JpaPredicate;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.restriction.Restriction;
import org.hibernate.relational.SchemaManager;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.SourceType;
import org.hibernate.type.descriptor.java.JavaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate ORM EmbeddingStore Implementation
 */
// Needed for inherited bean injection validation
public class HibernateEmbeddingStore<E> implements EmbeddingStore<TextSegment> {
    private static final Logger log = LoggerFactory.getLogger(HibernateEmbeddingStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final boolean IS_HIBERNATE_ORM_7_1;

    static {
        boolean isHibernateOrm71 = false;
        try {
            SchemaManager.class.getMethod("truncateTable", String.class);
        } catch (NoSuchMethodException e) {
            isHibernateOrm71 = true;
        }
        IS_HIBERNATE_ORM_7_1 = isHibernateOrm71;
    }

    protected final boolean isDynamic;
    protected final SessionFactory sessionFactory;
    protected final DatabaseKind databaseKind;
    protected final Class<E> entityClass;
    protected final EntityPersister entityPersister;
    protected final JavaType<Object> idType;
    protected final Generator idGenerator;
    protected final boolean allowUuidGeneration;
    protected final AttributeMapping idAttributeMapping;
    protected final AttributeMapping embeddingAttributeMapping;
    protected final AttributeMapping embeddedTextAttributeMapping;
    protected final AttributeMapping unmappedMetadataAttributeMapping;
    protected final Type<Map<?, ?>> unmappedMetadataAttributeMapType;
    protected final Map<String, AttributeMapping> metadataAttributeMappings;
    protected final DistanceFunction distanceFunction;

    private final JpaCriteriaDelete<?> deleteByIds;
    private final JpaCriteriaInsertValues<?> insertValues;

    /**
     * Constructor for HibernateEmbeddingStore Class
     *
     * @param isDynamic                     Whether the session factory was created dynamically
     * @param sessionFactory                The Hibernate session factory to use
     * @param databaseKind                  The database kind
     * @param entityClass                   The Hibernate entity class to use
     * @param embeddingAttributeName        The name of the entity attribute containing the embedding vector
     * @param embeddedTextAttributeName     The name of the entity attribute containing the text from which the embedding vector is derived, or null
     * @param unmappedMetadataAttributeName The name of the entity attribute to store generic metadata in
     * @param metadataAttributePaths        The name of the explicit metadata entity attributes
     * @param distanceFunction              The distance function to use for vector search
     */
    protected HibernateEmbeddingStore(
            boolean isDynamic,
            SessionFactory sessionFactory,
            DatabaseKind databaseKind,
            Class<E> entityClass,
            String embeddingAttributeName,
            String embeddedTextAttributeName,
            String unmappedMetadataAttributeName,
            String[] metadataAttributePaths,
            DistanceFunction distanceFunction) {
        this.isDynamic = isDynamic;
        this.sessionFactory = ensureNotNull(sessionFactory, "sessionFactory");
        this.databaseKind = ensureNotNull(databaseKind, "databaseKind");
        this.entityClass = ensureNotNull(entityClass, "entityClass");
        this.distanceFunction = ensureNotNull(distanceFunction, "distanceFunction");
        this.entityPersister = sessionFactory
                .unwrap(SessionFactoryImplementor.class)
                .getRuntimeMetamodels()
                .getMappingMetamodel()
                .getEntityDescriptor(entityClass);
        //noinspection unchecked
        this.idType = (JavaType<Object>) entityPersister.getIdentifierMapping().getJavaType();
        this.allowUuidGeneration =
                entityPersister.getIdentifierMapping().getJavaType().getJavaTypeClass() == String.class
                        || entityPersister.getIdentifierMapping().getJavaType().getJavaTypeClass() == UUID.class;
        this.idGenerator = entityPersister.getGenerator();
        this.idAttributeMapping = (AttributeMapping) entityPersister.getIdentifierMapping();
        this.embeddingAttributeMapping =
                entityPersister.findAttributeMapping(ensureNotEmpty(embeddingAttributeName, "embeddingAttributeName"));
        this.embeddedTextAttributeMapping = embeddedTextAttributeName == null
                ? null
                : entityPersister.findAttributeMapping(embeddedTextAttributeName);
        this.unmappedMetadataAttributeMapping = entityPersister.findAttributeMapping(
                ensureNotEmpty(unmappedMetadataAttributeName, "unmappedMetadataAttributeName"));
        if (embeddingAttributeMapping == null) {
            throw new IllegalArgumentException(
                    "Couldn't find embedding with attribute name: " + embeddingAttributeName);
        }
        if (embeddedTextAttributeMapping == null && embeddedTextAttributeName != null) {
            throw new IllegalArgumentException(
                    "Couldn't find embedded text with attribute name: " + embeddedTextAttributeName);
        }
        if (unmappedMetadataAttributeMapping == null) {
            throw new IllegalArgumentException(
                    "Couldn't find unmapped metadata with attribute name: " + unmappedMetadataAttributeName);
        }
        final Type<?> unmappedMetadataAttributeType = sessionFactory
                .getMetamodel()
                .entity(entityClass)
                .getSingularAttribute(unmappedMetadataAttributeName)
                .getType();
        if (unmappedMetadataAttributeType.getJavaType() == String.class) {
            this.unmappedMetadataAttributeMapType = null;
        } else {
            if (unmappedMetadataAttributeType.getJavaType() != Map.class) {
                throw new IllegalArgumentException("Unmapped metadata attribute '" + unmappedMetadataAttributeName
                        + "' must be of type Map or String, but found: "
                        + unmappedMetadataAttributeType.getJavaType().getTypeName());
            }
            //noinspection unchecked
            this.unmappedMetadataAttributeMapType = (Type<Map<?, ?>>) unmappedMetadataAttributeType;
        }
        if (metadataAttributePaths == null || metadataAttributePaths.length == 0) {
            this.metadataAttributeMappings = Collections.emptyMap();
        } else {
            final Map<String, AttributeMapping> metadataAttributeMappings =
                    new LinkedHashMap<>(metadataAttributePaths.length);
            for (String metadataAttributePath : metadataAttributePaths) {
                if (!(entityPersister.findByPath(metadataAttributePath) instanceof AttributeMapping attributeMapping)) {
                    throw new IllegalArgumentException(
                            "Couldn't find metadata attribute with path: " + metadataAttributePath);
                }
                metadataAttributeMappings.put(metadataAttributePath, attributeMapping);
            }
            this.metadataAttributeMappings = metadataAttributeMappings;
        }

        final HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder();
        final JpaCriteriaDelete<?> delete = criteriaBuilder.createCriteriaDelete(entityClass);
        @SuppressWarnings("unchecked")
        final JpaParameterExpression<List<?>> idListParameter =
                (JpaParameterExpression<List<?>>) (JpaParameterExpression<?>)
                        criteriaBuilder.listParameter(idType.getJavaTypeClass(), idAttributeMapping.getAttributeName());
        final JpaRoot<?> root = delete.getTarget();
        delete.where(criteriaBuilder
                .in(root.get(idAttributeMapping.getAttributeName()))
                .value(idListParameter));
        this.deleteByIds = delete;

        final JpaCriteriaInsertValues<?> criteriaInsertValues = criteriaBuilder.createCriteriaInsertValues(entityClass);
        final JpaRoot<?> target = criteriaInsertValues.getTarget();
        final JpaParameterExpression<Object> idParameter =
                criteriaBuilder.parameter(idType.getJavaTypeClass(), idAttributeMapping.getAttributeName());
        final JpaParameterExpression<float[]> embeddingParameter =
                criteriaBuilder.parameter(float[].class, embeddingAttributeName);
        final JpaParameterExpression<?> unmappedMetadataParameter = unmappedMetadataAttributeMapType != null
                ? criteriaBuilder.parameter(Map.class, unmappedMetadataAttributeName)
                : criteriaBuilder.parameter(String.class, unmappedMetadataAttributeName);
        final List<Path<?>> paths = new ArrayList<>();
        final List<Expression<?>> values = new ArrayList<>();
        final JpaConflictClause<?> onConflict =
                criteriaInsertValues.onConflict().conflictOnConstraintAttributes(idAttributeMapping.getAttributeName());
        final JpaRoot<?> excludedRoot = onConflict.getExcludedRoot();
        final JpaConflictUpdateAction<?> updateAction = onConflict.onConflictDoUpdate();

        paths.add(target.get(idAttributeMapping.getAttributeName()));
        values.add(idParameter);

        paths.add(target.get(embeddingAttributeName));
        values.add(embeddingParameter);
        updateAction.set(embeddingAttributeName, excludedRoot.get(embeddingAttributeName));

        if (embeddedTextAttributeName != null) {
            final JpaParameterExpression<String> embeddedTextParameter =
                    criteriaBuilder.parameter(String.class, embeddedTextAttributeName);
            paths.add(target.get(embeddedTextAttributeName));
            values.add(embeddedTextParameter);
            updateAction.set(embeddedTextAttributeName, excludedRoot.get(embeddedTextAttributeName));
        }

        paths.add(target.get(unmappedMetadataAttributeName));
        values.add(unmappedMetadataParameter);
        updateAction.set(unmappedMetadataAttributeName, excludedRoot.get(unmappedMetadataAttributeName));

        for (String attributePath : metadataAttributeMappings.keySet()) {
            JpaPath<Object> path = get(target, attributePath);
            paths.add(path);
            values.add(criteriaBuilder.parameter(path.getJavaType(), attributePath));
            updateAction.set(path, (Object) get(excludedRoot, attributePath));
        }

        criteriaInsertValues.setInsertionTargetPaths(paths);
        criteriaInsertValues.values(Collections.singletonList(criteriaBuilder.values(values)));

        this.insertValues = criteriaInsertValues;
    }

    public HibernateEmbeddingStore() {
        this.isDynamic = false;
        this.sessionFactory = null;
        this.databaseKind = null;
        this.entityClass = null;
        this.entityPersister = null;
        this.idType = null;
        this.idGenerator = null;
        this.allowUuidGeneration = false;
        this.idAttributeMapping = null;
        this.embeddingAttributeMapping = null;
        this.embeddedTextAttributeMapping = null;
        this.unmappedMetadataAttributeMapping = null;
        this.unmappedMetadataAttributeMapType = null;
        this.metadataAttributeMappings = null;
        this.distanceFunction = null;
        this.deleteByIds = null;
        this.insertValues = null;
    }

    /**
     * A builder for creating a Hibernate based {@link EmbeddingStore} for
     * an existing {@link SessionFactory} and entity classes.
     *
     * @return The builder
     */
    public static <E> Builder<E> builder(Class<E> entityClass) {
        return new Builder<>(entityClass);
    }

    /**
     * A builder for creating a Hibernate based {@link EmbeddingStore} when
     * no {@link SessionFactory} or entity classes exist.
     *
     * @return The builder
     */
    public static DynamicBuilder dynamicBuilder() {
        return new DynamicBuilder();
    }

    /**
     * A builder for creating a Hibernate based {@link EmbeddingStore} when
     * no {@link SessionFactory} or entity classes exist and a datasource shall be used.
     *
     * @return The builder
     */
    public static DynamicDatasourceBuilder dynamicDatasourceBuilder() {
        return new DynamicDatasourceBuilder();
    }

    public void close() {
        if (isDynamic) {
            this.sessionFactory.close();
        }
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param embedding The embedding to be added to the store.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding) {
        final List<String> ids = addAll(Collections.singletonList(embedding), null);
        return ids.get(0);
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param id        The unique identifier for the embedding to be added.
     * @param embedding The embedding to be added to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    /**
     * Adds a given embedding and the corresponding content that has been embedded to the store.
     *
     * @param embedding   The embedding to be added to the store.
     * @param textSegment Original content that was embedded.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        final List<String> ids = addAll(
                Collections.singletonList(embedding),
                textSegment == null ? null : Collections.singletonList(textSegment));
        return ids.get(0);
    }

    /**
     * Adds multiple embeddings to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        sessionFactory.inTransaction(session -> {
            session.createMutationQuery(deleteByIds)
                    .setParameter(
                            idAttributeMapping.getAttributeName(),
                            ids.stream().map(idType::fromString).collect(Collectors.toList()))
                    .executeUpdate();
        });
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        final HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder();
        final JpaCriteriaDelete<?> delete = criteriaBuilder.createCriteriaDelete(entityClass);
        delete.where(createPredicateFromFilter(delete.getTarget(), filter, criteriaBuilder));
        sessionFactory.inTransaction(session -> {
            session.createMutationQuery(delete).executeUpdate();
        });
    }

    @Override
    public void removeAll() {
        if (isDynamic) {
            sessionFactory.getSchemaManager().truncate();
        } else if (isIsHibernateOrm71()) {
            sessionFactory.inStatelessTransaction(session -> {
                session.createMutationQuery("delete from " + entityPersister.getEntityName())
                        .executeUpdate();
            });
        } else {
            sessionFactory
                    .getSchemaManager()
                    .truncateTable(entityPersister.getIdentifierTableMapping().getTableName());
        }
    }

    // Allows replacing this logic for native image generation
    private static boolean isIsHibernateOrm71() {
        return IS_HIBERNATE_ORM_7_1;
    }

    /**
     * Searches for the most similar (closest in the embedding space) entities based on {@link Embedding}s.
     * <br>
     * The passed restriction is used to filter by metadata.
     *
     * @param embedding The embedding for the query
     * @param restriction Filter for metadata
     * @return The list of all found entities.
     */
    public List<E> query(Embedding embedding, Restriction<E> restriction) {
        return query(embedding, null, restriction, null);
    }

    /**
     * Searches for the most similar (closest in the embedding space) entities based on {@link Embedding}s.
     * <br>
     * The passed restriction is used to filter by metadata.
     *
     * @param embedding The embedding for the query
     * @param minScore The minimum distance score
     * @param restriction Filter for metadata
     * @return The list of all found entities.
     */
    public List<E> query(Embedding embedding, double minScore, Restriction<E> restriction) {
        return query(embedding, minScore, restriction, null);
    }

    /**
     * Searches for the most similar (closest in the embedding space) entities based on {@link Embedding}s.
     * <br>
     * The passed restriction is used to filter by metadata.
     *
     * @param embedding The embedding for the query
     * @param minScore The minimum distance score
     * @param restriction Filter for metadata
     * @param maxResults The maximum number of results
     * @return The list of all found entities.
     */
    public List<E> query(Embedding embedding, double minScore, Restriction<E> restriction, int maxResults) {
        return query(embedding, (Double) minScore, restriction, (Integer) maxResults);
    }

    private List<E> query(Embedding embedding, Double minScore, Restriction<E> restriction, Integer maxResults) {
        ensureNotNull(restriction, "restriction");

        final JpaCriteriaQuery<E> query = createBaseQuery(entityClass, minScore != null, restriction::toPredicate);

        return sessionFactory.fromStatelessSession(session -> {
            final SelectionQuery<E> selectionQuery = session.createSelectionQuery(query);
            selectionQuery.setParameter(embeddingAttributeMapping.getAttributeName(), embedding.vector());
            if (minScore != null) {
                selectionQuery.setParameter("minScore", minScore);
            }
            if (maxResults != null) {
                selectionQuery.setMaxResults(maxResults);
            }
            return selectionQuery.getResultList();
        });
    }

    /**
     * Searches for the most similar (closest in the embedding space) {@link Embedding}s.
     * <br>
     * The passed restriction is used to filter by metadata.
     *
     * @param embedding The embedding for the search
     * @param restriction Filter for metadata
     * @return An {@link EmbeddingSearchResult} containing all found {@link Embedding}s.
     */
    public EmbeddingSearchResult<TextSegment> search(Embedding embedding, Restriction<E> restriction) {
        return search(embedding, null, restriction, null);
    }

    /**
     * Searches for the most similar (closest in the embedding space) {@link Embedding}s.
     * <br>
     * The passed restriction is used to filter by metadata.
     *
     * @param embedding The embedding for the search
     * @param minScore The minimum distance score
     * @param restriction Filter for metadata
     * @return An {@link EmbeddingSearchResult} containing all found {@link Embedding}s.
     */
    public EmbeddingSearchResult<TextSegment> search(Embedding embedding, double minScore, Restriction<E> restriction) {
        return search(embedding, minScore, restriction, null);
    }

    /**
     * Searches for the most similar (closest in the embedding space) {@link Embedding}s.
     * <br>
     * The passed restriction is used to filter by metadata.
     *
     * @param embedding The embedding for the search
     * @param minScore The minimum distance score
     * @param restriction Filter for metadata
     * @param maxResults The maximum number of results
     * @return An {@link EmbeddingSearchResult} containing all found {@link Embedding}s.
     */
    public EmbeddingSearchResult<TextSegment> search(
            Embedding embedding, double minScore, Restriction<E> restriction, int maxResults) {
        return search(embedding, (Double) minScore, restriction, (Integer) maxResults);
    }

    private EmbeddingSearchResult<TextSegment> search(
            Embedding embedding, Double minScore, Restriction<E> restriction, Integer maxResults) {
        ensureNotNull(restriction, "restriction");

        final JpaCriteriaQuery<Object[]> query =
                createBaseQuery(Object[].class, minScore != null, restriction::toPredicate);
        applyEmbeddingSearchResultSelections(query);

        return sessionFactory.fromStatelessSession(session -> {
            final SelectionQuery<Object[]> selectionQuery = session.createSelectionQuery(query);
            selectionQuery.setParameter(embeddingAttributeMapping.getAttributeName(), embedding.vector());
            if (minScore != null) {
                selectionQuery.setParameter("minScore", minScore);
            }
            if (maxResults != null) {
                selectionQuery.setMaxResults(maxResults);
            }

            return transformToSearchResult(selectionQuery.getResultList());
        });
    }

    /**
     * Searches for the most similar (closest in the embedding space) {@link Embedding}s.
     * <br>
     * All search criteria are defined inside the {@link EmbeddingSearchRequest}.
     * <br>
     * {@link EmbeddingSearchRequest#filter()} is used to filter by meta data.
     *
     * @param request A request to search in an {@link EmbeddingStore}. Contains all search criteria.
     * @return An {@link EmbeddingSearchResult} containing all found {@link Embedding}s.
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding referenceEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        final JpaCriteriaQuery<Object[]> query = createBaseQuery(
                Object[].class,
                true,
                (root, cb) -> filter == null ? null : createPredicateFromFilter(root, filter, cb));
        applyEmbeddingSearchResultSelections(query);

        return sessionFactory.fromStatelessSession(session -> {
            final SelectionQuery<Object[]> selectionQuery = session.createSelectionQuery(query);
            selectionQuery.setParameter(embeddingAttributeMapping.getAttributeName(), referenceEmbedding.vector());
            selectionQuery.setParameter("minScore", minScore);
            selectionQuery.setMaxResults(maxResults);

            return transformToSearchResult(selectionQuery.getResultList());
        });
    }

    private EmbeddingSearchResult<TextSegment> transformToSearchResult(List<Object[]> tuples) {
        final List<EmbeddingMatch<TextSegment>> result = new ArrayList<>(tuples.size());
        for (Object[] tuple : tuples) {
            final Double score = (Double) tuple[0];
            final Object embeddingId = tuple[1];
            final Embedding embedding = new Embedding((float[]) tuple[2]);
            final String text = embeddedTextAttributeMapping == null ? null : (String) tuple[4];
            TextSegment segment = null;
            if (isNotNullOrBlank(text)) {
                final Object textMetadata = tuple[3];
                final Metadata metadata;
                if (textMetadata instanceof String metadataJson) {
                    try {
                        //noinspection unchecked
                        metadata = new Metadata(OBJECT_MAPPER.readValue(getOrDefault(metadataJson, "{}"), Map.class));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                } else if (textMetadata instanceof Map<?, ?> metadataMap) {
                    //noinspection unchecked
                    metadata = new Metadata((Map<String, ?>) metadataMap);
                } else if (textMetadata == null) {
                    metadata = new Metadata();
                } else {
                    throw new IllegalArgumentException(
                            "Text metadata must be of type String or Map but got: " + textMetadata.getClass());
                }

                int i = 0;
                for (Map.Entry<String, AttributeMapping> metadataAttribute : metadataAttributeMappings.entrySet()) {
                    final String metadataAttributePath = metadataAttribute.getKey();
                    final JavaType<?> metadataAttributeJavaType =
                            metadataAttribute.getValue().getJavaType();
                    final Object metadataValue = tuple[5 + i];
                    if (metadataValue != null) {
                        if (metadataValue instanceof String string) {
                            metadata.put(metadataAttributePath, string);
                        } else if (metadataValue instanceof UUID uuid) {
                            metadata.put(metadataAttributePath, uuid);
                        } else if (metadataValue instanceof Integer integerValue) {
                            metadata.put(metadataAttributePath, integerValue);
                        } else if (metadataValue instanceof Long longValue) {
                            metadata.put(metadataAttributePath, longValue);
                        } else if (metadataValue instanceof Float floatValue) {
                            metadata.put(metadataAttributePath, floatValue);
                        } else if (metadataValue instanceof Double doubleValue) {
                            metadata.put(metadataAttributePath, doubleValue);
                        } else {
                            //noinspection unchecked
                            metadata.put(
                                    metadataAttributePath,
                                    ((JavaType<Object>) metadataAttributeJavaType).toString(metadataValue));
                        }
                    }
                    i++;
                }

                segment = TextSegment.from(text, metadata);
            }
            result.add(new EmbeddingMatch<>(score, idType.toString(embeddingId), embedding, segment));
        }
        return new EmbeddingSearchResult<>(result);
    }

    private <T> JpaCriteriaQuery<T> createBaseQuery(
            Class<T> resultClass,
            boolean minScoreFilter,
            BiFunction<JpaRoot<E>, HibernateCriteriaBuilder, Predicate> additionalPredicateBuilder) {
        final HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder();
        final JpaCriteriaQuery<T> query = criteriaBuilder.createQuery(resultClass);
        final JpaRoot<E> root = query.from(entityClass);
        final JpaPath<float[]> embeddingPath = root.get(embeddingAttributeMapping.getAttributeName());
        // Workaround because the VectorArgumentValidator can't deal with float[] expressions
        @SuppressWarnings("unchecked")
        final JpaParameterExpression<float[]> embeddingParameter =
                (JpaParameterExpression<float[]>) (JpaParameterExpression<?>) criteriaBuilder.parameter(
                        HibernateEmbeddingStore.class, embeddingAttributeMapping.getAttributeName());
        final Expression<Double> distance =
                distance(distanceFunction, embeddingPath, embeddingParameter, criteriaBuilder);

        final Predicate predicate = minScoreFilter
                ? distanceFilter(
                        distanceFunction,
                        distance,
                        criteriaBuilder.parameter(Double.class, "minScore"),
                        criteriaBuilder)
                : null;
        final Predicate additonalPredicate =
                additionalPredicateBuilder == null ? null : additionalPredicateBuilder.apply(root, criteriaBuilder);
        query.where(
                additonalPredicate == null
                        ? predicate
                        : (predicate == null
                                ? additonalPredicate
                                : criteriaBuilder.and(predicate, additonalPredicate)));
        query.orderBy(criteriaBuilder.asc(distance));
        return query;
    }

    private void applyEmbeddingSearchResultSelections(JpaCriteriaQuery<Object[]> query) {
        final HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder();
        final JpaRoot<?> root = (JpaRoot<?>) query.getRoots().iterator().next();
        @SuppressWarnings("unchecked")
        final Expression<Double> distance =
                (Expression<Double>) query.getOrderList().get(0).getExpression();
        final int metadataOffset = embeddedTextAttributeMapping == null ? 4 : 5;
        final Selection<?>[] selections = new Selection<?>[metadataOffset + metadataAttributeMappings.size()];
        selections[0] = score(distanceFunction, distance, criteriaBuilder);
        selections[1] = root.get(idAttributeMapping.getAttributeName());
        selections[2] = root.get(embeddingAttributeMapping.getAttributeName());
        selections[3] = root.get(unmappedMetadataAttributeMapping.getAttributeName());
        if (embeddedTextAttributeMapping != null) {
            selections[4] = root.get(embeddedTextAttributeMapping.getAttributeName());
        }
        int index = metadataOffset;
        for (String attributePath : metadataAttributeMappings.keySet()) {
            selections[index++] = get(root, attributePath);
        }
        query.select(criteriaBuilder.array(selections));
    }

    private Expression<Double> distance(
            DistanceFunction distanceFunction,
            Expression<float[]> lhs,
            Expression<float[]> rhs,
            CriteriaBuilder criteriaBuilder) {
        final String functionName =
                switch (distanceFunction) {
                    case COSINE -> "cosine_distance";
                    case EUCLIDEAN -> "euclidean_distance";
                    case EUCLIDEAN_SQUARED -> "euclidean_square_distance";
                    case MANHATTAN -> "taxicab_distance";
                    case INNER_PRODUCT -> "inner_product";
                    case NEGATIVE_INNER_PRODUCT -> "negative_inner_product";
                    case HAMMING -> "hamming_distance";
                    case JACCARD -> "jaccard_distance";
                };
        return criteriaBuilder.function(functionName, Double.class, lhs, rhs);
    }

    protected Expression<Double> score(
            DistanceFunction distanceFunction, Expression<Double> distance, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder
                .quot(criteriaBuilder.diff(criteriaBuilder.literal(2D), distance), criteriaBuilder.literal(2D))
                .as(Double.class);
    }

    protected Predicate distanceFilter(
            DistanceFunction distanceFunction,
            Expression<Double> distance,
            Expression<Double> minScore,
            CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.le(
                distance,
                criteriaBuilder.function(
                        "round",
                        Double.class,
                        criteriaBuilder.diff(
                                criteriaBuilder.literal(2D),
                                criteriaBuilder.prod(criteriaBuilder.literal(2D), minScore)),
                        criteriaBuilder.literal(8)));
    }

    private <X> Predicate createPredicateFromFilter(
            JpaRoot<X> root, Filter filter, final HibernateCriteriaBuilder criteriaBuilder) {
        if (filter instanceof ContainsString containsString) {
            return mapContains(root, criteriaBuilder, containsString);
        } else if (filter instanceof IsEqualTo isEqualTo) {
            return mapEqual(root, criteriaBuilder, isEqualTo);
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return mapNotEqual(root, criteriaBuilder, isNotEqualTo);
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return mapGreaterThan(root, criteriaBuilder, isGreaterThan);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual(root, criteriaBuilder, isGreaterThanOrEqualTo);
        } else if (filter instanceof IsLessThan isLessThan) {
            return mapLessThan(root, criteriaBuilder, isLessThan);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return mapLessThanOrEqual(root, criteriaBuilder, isLessThanOrEqualTo);
        } else if (filter instanceof IsIn isIn) {
            return mapIn(root, criteriaBuilder, isIn);
        } else if (filter instanceof IsNotIn isNotIn) {
            return mapNotIn(root, criteriaBuilder, isNotIn);
        } else if (filter instanceof And and) {
            return mapAnd(root, criteriaBuilder, and);
        } else if (filter instanceof Not not) {
            return mapNot(root, criteriaBuilder, not);
        } else if (filter instanceof Or or) {
            return mapOr(root, criteriaBuilder, or);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private <X> JpaPath<X> get(JpaRoot<?> root, String path) {
        if (path.indexOf('.') == -1) {
            return root.get(path);
        } else {
            JpaPath<?> p = root;
            StringTokenizer tokenizer = new StringTokenizer(path, ".");
            while (tokenizer.hasMoreTokens()) {
                p = p.get(tokenizer.nextToken());
            }
            //noinspection unchecked
            return (JpaPath<X>) p;
        }
    }

    private Object toDomainValue(JavaType<?> attributeJavaType, Object value) {
        if (attributeJavaType.getJavaTypeClass() == String.class) {
            return value.toString();
        } else {
            return value instanceof String s ? attributeJavaType.fromString(s) : value;
        }
    }

    private JpaPredicate mapContains(
            JpaRoot<?> root, HibernateCriteriaBuilder criteriaBuilder, ContainsString containsString) {
        final AttributeMapping attributeMapping = metadataAttributeMappings.get(containsString.key());
        final JpaExpression<String> expression = attributeMapping != null
                ? get(root, containsString.key())
                : criteriaBuilder.jsonValue(
                        root.get(unmappedMetadataAttributeMapping.getAttributeName()),
                        criteriaBuilder.literal("$." + containsString.key()));
        return criteriaBuilder.and(
                expression.isNotNull(),
                criteriaBuilder.like(
                        expression,
                        "%"
                                + containsString
                                        .comparisonValue()
                                        .replace("\\", "\\\\")
                                        .replace("%", "\\%")
                                        .replace("?", "\\?")
                                + "%",
                        '\\'));
    }

    private JpaPredicate mapEqual(JpaRoot<?> root, HibernateCriteriaBuilder criteriaBuilder, IsEqualTo isEqualTo) {
        final AttributeMapping attributeMapping = metadataAttributeMappings.get(isEqualTo.key());
        if (attributeMapping != null) {
            final JpaExpression<?> valueExpression = get(root, isEqualTo.key());
            final Object comparisonValue = isEqualTo.comparisonValue();
            final Object domainValue = toDomainValue(attributeMapping.getJavaType(), comparisonValue);
            return criteriaBuilder.and(
                    valueExpression.isNotNull(), criteriaBuilder.equal(valueExpression, domainValue));
        } else {
            final JpaExpression<?> valueExpression = criteriaBuilder.jsonValue(
                    root.get(unmappedMetadataAttributeMapping.getAttributeName()),
                    criteriaBuilder.literal("$." + isEqualTo.key()),
                    isEqualTo.comparisonValue().getClass());
            return criteriaBuilder.and(
                    valueExpression.isNotNull(), criteriaBuilder.equal(valueExpression, isEqualTo.comparisonValue()));
        }
    }

    private JpaPredicate mapNotEqual(
            JpaRoot<?> root, HibernateCriteriaBuilder criteriaBuilder, IsNotEqualTo isNotEqualTo) {
        final AttributeMapping attributeMapping = metadataAttributeMappings.get(isNotEqualTo.key());
        if (attributeMapping != null) {
            final JpaExpression<?> valueExpression = get(root, isNotEqualTo.key());
            final Object comparisonValue = isNotEqualTo.comparisonValue();
            final Object domainValue = toDomainValue(attributeMapping.getJavaType(), comparisonValue);
            return criteriaBuilder.or(valueExpression.isNull(), criteriaBuilder.notEqual(valueExpression, domainValue));
        } else {
            final JpaExpression<?> valueExpression = criteriaBuilder.jsonValue(
                    root.get(unmappedMetadataAttributeMapping.getAttributeName()),
                    criteriaBuilder.literal("$." + isNotEqualTo.key()),
                    isNotEqualTo.comparisonValue().getClass());
            return criteriaBuilder.or(
                    valueExpression.isNull(),
                    criteriaBuilder.notEqual(valueExpression, isNotEqualTo.comparisonValue()));
        }
    }

    private JpaPredicate mapIn(JpaRoot<?> root, HibernateCriteriaBuilder criteriaBuilder, IsIn isIn) {
        final AttributeMapping attributeMapping = metadataAttributeMappings.get(isIn.key());
        if (attributeMapping != null) {
            final Collection<Object> domainValue = isIn.comparisonValues().stream()
                    .map(value -> toDomainValue(attributeMapping.getJavaType(), value))
                    .collect(Collectors.toList());
            return criteriaBuilder.in(get(root, isIn.key()), domainValue);
        } else {
            return criteriaBuilder.in(
                    criteriaBuilder.jsonValue(
                            root.get(unmappedMetadataAttributeMapping.getAttributeName()),
                            criteriaBuilder.literal("$." + isIn.key())),
                    isIn.comparisonValues().stream().map(Object::toString).collect(Collectors.toList()));
        }
    }

    private JpaPredicate mapNotIn(JpaRoot<?> root, HibernateCriteriaBuilder criteriaBuilder, IsNotIn isNotIn) {
        final AttributeMapping attributeMapping = metadataAttributeMappings.get(isNotIn.key());
        if (attributeMapping != null) {
            final JpaExpression<?> valueExpression = get(root, isNotIn.key());
            final Collection<Object> domainValue = isNotIn.comparisonValues().stream()
                    .map(value -> toDomainValue(attributeMapping.getJavaType(), value))
                    .collect(Collectors.toList());
            return criteriaBuilder.or(
                    valueExpression.isNull(),
                    criteriaBuilder.in(valueExpression, domainValue).not());
        } else {
            final JpaExpression<String> valueExpression = criteriaBuilder.jsonValue(
                    root.get(unmappedMetadataAttributeMapping.getAttributeName()),
                    criteriaBuilder.literal("$." + isNotIn.key()));
            return criteriaBuilder.or(
                    valueExpression.isNull(),
                    criteriaBuilder
                            .in(
                                    valueExpression,
                                    isNotIn.comparisonValues().stream()
                                            .map(Object::toString)
                                            .collect(Collectors.toList()))
                            .not());
        }
    }

    private JpaPredicate mapNot(JpaRoot<?> root, HibernateCriteriaBuilder criteriaBuilder, Not not) {
        return criteriaBuilder.not(createPredicateFromFilter(root, not.expression(), criteriaBuilder));
    }

    private JpaPredicate mapAnd(JpaRoot<?> root, HibernateCriteriaBuilder criteriaBuilder, And and) {
        return criteriaBuilder.and(
                createPredicateFromFilter(root, and.left(), criteriaBuilder),
                createPredicateFromFilter(root, and.right(), criteriaBuilder));
    }

    private JpaPredicate mapOr(JpaRoot<?> root, HibernateCriteriaBuilder criteriaBuilder, Or or) {
        return criteriaBuilder.or(
                createPredicateFromFilter(root, or.left(), criteriaBuilder),
                createPredicateFromFilter(root, or.right(), criteriaBuilder));
    }

    private <X, Y extends Comparable<? super Y>> JpaPredicate mapGreaterThan(
            JpaRoot<X> root, HibernateCriteriaBuilder criteriaBuilder, IsGreaterThan isGreaterThan) {
        final AttributeMapping attributeMapping = metadataAttributeMappings.get(isGreaterThan.key());
        if (attributeMapping != null) {
            final Object comparisonValue = isGreaterThan.comparisonValue();
            if (attributeMapping.getJavaType().getJavaTypeClass() == String.class
                    && !(comparisonValue instanceof String)) {
                //noinspection unchecked
                return criteriaBuilder.greaterThan(
                        get(root, isGreaterThan.key()).cast((Class<Y>) comparisonValue.getClass()),
                        (Y) comparisonValue);
            } else {
                final Object domainValue = toDomainValue(attributeMapping.getJavaType(), comparisonValue);
                //noinspection unchecked
                return criteriaBuilder.greaterThan(get(root, isGreaterThan.key()), (Y) domainValue);
            }
        } else {
            //noinspection unchecked
            return criteriaBuilder.greaterThan(
                    criteriaBuilder.jsonValue(
                            root.get(unmappedMetadataAttributeMapping.getAttributeName()),
                            criteriaBuilder.literal("$." + isGreaterThan.key()),
                            (Class<Y>) isGreaterThan.comparisonValue().getClass()),
                    (Y) isGreaterThan.comparisonValue());
        }
    }

    private <X, Y extends Comparable<? super Y>> JpaPredicate mapGreaterThanOrEqual(
            JpaRoot<X> root, HibernateCriteriaBuilder criteriaBuilder, IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        final AttributeMapping attributeMapping = metadataAttributeMappings.get(isGreaterThanOrEqualTo.key());
        if (attributeMapping != null) {
            final Object comparisonValue = isGreaterThanOrEqualTo.comparisonValue();
            if (attributeMapping.getJavaType().getJavaTypeClass() == String.class
                    && !(comparisonValue instanceof String)) {
                //noinspection unchecked
                return criteriaBuilder.greaterThanOrEqualTo(
                        get(root, isGreaterThanOrEqualTo.key()).cast((Class<Y>) comparisonValue.getClass()),
                        (Y) comparisonValue);
            } else {
                final Object domainValue = toDomainValue(attributeMapping.getJavaType(), comparisonValue);
                //noinspection unchecked
                return criteriaBuilder.greaterThanOrEqualTo(get(root, isGreaterThanOrEqualTo.key()), (Y) domainValue);
            }
        } else {
            //noinspection unchecked
            return criteriaBuilder.greaterThanOrEqualTo(
                    criteriaBuilder.jsonValue(
                            root.get(unmappedMetadataAttributeMapping.getAttributeName()),
                            criteriaBuilder.literal("$." + isGreaterThanOrEqualTo.key()),
                            (Class<Y>) isGreaterThanOrEqualTo.comparisonValue().getClass()),
                    (Y) isGreaterThanOrEqualTo.comparisonValue());
        }
    }

    private <X, Y extends Comparable<? super Y>> JpaPredicate mapLessThan(
            JpaRoot<X> root, HibernateCriteriaBuilder criteriaBuilder, IsLessThan isLessThan) {
        final AttributeMapping attributeMapping = metadataAttributeMappings.get(isLessThan.key());
        if (attributeMapping != null) {
            final Object comparisonValue = isLessThan.comparisonValue();
            if (attributeMapping.getJavaType().getJavaTypeClass() == String.class
                    && !(comparisonValue instanceof String)) {
                //noinspection unchecked
                return criteriaBuilder.lessThan(
                        get(root, isLessThan.key()).cast((Class<Y>) comparisonValue.getClass()), (Y) comparisonValue);
            } else {
                final Object domainValue = toDomainValue(attributeMapping.getJavaType(), comparisonValue);
                //noinspection unchecked
                return criteriaBuilder.lessThan(get(root, isLessThan.key()), (Y) domainValue);
            }
        } else {
            //noinspection unchecked
            return criteriaBuilder.lessThan(
                    criteriaBuilder.jsonValue(
                            root.get(unmappedMetadataAttributeMapping.getAttributeName()),
                            criteriaBuilder.literal("$." + isLessThan.key()),
                            (Class<Y>) isLessThan.comparisonValue().getClass()),
                    (Y) isLessThan.comparisonValue());
        }
    }

    private <X, Y extends Comparable<? super Y>> JpaPredicate mapLessThanOrEqual(
            JpaRoot<X> root, HibernateCriteriaBuilder criteriaBuilder, IsLessThanOrEqualTo isLessThanOrEqualTo) {
        final AttributeMapping attributeMapping = metadataAttributeMappings.get(isLessThanOrEqualTo.key());
        if (attributeMapping != null) {
            final Object comparisonValue = isLessThanOrEqualTo.comparisonValue();
            if (attributeMapping.getJavaType().getJavaTypeClass() == String.class
                    && !(comparisonValue instanceof String)) {
                //noinspection unchecked
                return criteriaBuilder.lessThanOrEqualTo(
                        get(root, isLessThanOrEqualTo.key()).cast((Class<Y>) comparisonValue.getClass()),
                        (Y) comparisonValue);
            } else {
                final Object domainValue = toDomainValue(attributeMapping.getJavaType(), comparisonValue);
                //noinspection unchecked
                return criteriaBuilder.lessThanOrEqualTo(get(root, isLessThanOrEqualTo.key()), (Y) domainValue);
            }
        } else {
            //noinspection unchecked
            return criteriaBuilder.lessThanOrEqualTo(
                    criteriaBuilder.jsonValue(
                            root.get(unmappedMetadataAttributeMapping.getAttributeName()),
                            criteriaBuilder.literal("$." + isLessThanOrEqualTo.key()),
                            (Class<Y>) isLessThanOrEqualTo.comparisonValue().getClass()),
                    (Y) isLessThanOrEqualTo.comparisonValue());
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    public void addAllEntities(List<?> entities) {
        if (isNullOrEmpty(entities)) {
            log.info("Empty entities - no ops");
            return;
        }
        sessionFactory.inStatelessTransaction(session -> session.insertMultiple(entities));
    }

    @Override
    public List<String> generateIds(final int n) {
        final List<Object> ids = sessionFactory.fromStatelessTransaction(
                session -> generateIds(n, (SharedSessionContractImplementor) session));
        final ArrayList<String> idStrings = new ArrayList<>(ids.size());
        for (Object id : ids) {
            idStrings.add(idType.toString(id));
        }
        return idStrings;
    }

    private ArrayList<Object> generateIds(final int n, SharedSessionContractImplementor session) {
        if (!idGenerator.allowAssignedIdentifiers()) {
            throw new IllegalStateException(
                    "Entity does not allow generating identifiers and assigning them separately");
        }
        if (idGenerator instanceof BeforeExecutionGenerator beforeExecutionGenerator) {
            final ArrayList<Object> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ids.add(beforeExecutionGenerator.generate(session, null, null, EventType.INSERT));
            }
            return ids;
        } else if (allowUuidGeneration) {
            // Assigned identifier. Let's support generating UUID automatically as a convenience
            final ArrayList<Object> ids = new ArrayList<>(n);
            if (String.class.isAssignableFrom(idType.getJavaTypeClass())) {
                for (int i = 0; i < n; i++) {
                    ids.add(randomUUID());
                }
            } else {
                for (int i = 0; i < n; i++) {
                    ids.add(UUID.randomUUID());
                }
            }
            return ids;
        } else {
            throw new IllegalStateException("Can't generate identifiers for identifier type "
                    + idType.getJavaTypeClass().getName() + " without a generator");
        }
    }

    @Override
    public List<String> addAll(final List<Embedding> embeddings, final List<TextSegment> embedded) {
        // todo: make this configurable or always work with entities directly?
        if (!idGenerator.allowAssignedIdentifiers() || idGenerator.generatedOnExecution()) {
            if (isNullOrEmpty(embeddings)) {
                log.info("Empty embeddings - no ops");
                return Collections.emptyList();
            }
            ensureTrue(
                    embedded == null || embeddings.size() == embedded.size(),
                    "embeddings size is not equal to embedded size");
            final ArrayList<Object> entities = createEntities(embeddings, embedded);

            sessionFactory.inStatelessTransaction(session -> {
                if (!idGenerator.generatesSometimes() && allowUuidGeneration) {
                    final SharedSessionContractImplementor sharedSessionContractImplementor =
                            (SharedSessionContractImplementor) session;
                    final boolean convertToString = String.class.isAssignableFrom(idType.getJavaTypeClass());
                    for (Object entity : entities) {
                        final UUID uuid = UUID.randomUUID();
                        final Object id = convertToString ? uuid.toString() : uuid;
                        entityPersister.setIdentifier(entity, id, sharedSessionContractImplementor);
                    }
                }
                session.insertMultiple(entities);
            });
            final ArrayList<String> idStrings = new ArrayList<>(embeddings.size());
            for (Object entity : entities) {
                idStrings.add(idType.toString(entityPersister.getIdentifier(entity)));
            }
            return idStrings;
        } else {
            return sessionFactory.fromStatelessTransaction(session -> {
                final ArrayList<Object> ids =
                        generateIds(embeddings.size(), (SharedSessionContractImplementor) session);
                addAll(ids, embeddings, embedded, session);
                final ArrayList<String> idStrings = new ArrayList<>(ids.size());
                for (Object id : ids) {
                    idStrings.add(idType.toString(id));
                }
                return idStrings;
            });
        }
    }

    @Override
    public void addAll(List<String> idStrings, List<Embedding> embeddings, List<TextSegment> embedded) {
        final ArrayList<Object> ids = new ArrayList<>(idStrings.size());
        for (String id : idStrings) {
            ids.add(idType.fromString(id));
        }
        sessionFactory.inStatelessTransaction(session -> {
            addAll(ids, embeddings, embedded, session);
        });
    }

    private ArrayList<Object> createEntities(final List<Embedding> embeddings, final List<TextSegment> embedded) {
        final EntityInstantiator instantiator =
                entityPersister.getRepresentationStrategy().getInstantiator();
        final ArrayList<Object> entities = new ArrayList<>(embeddings.size());
        final Object[] values = new Object[entityPersister.getNumberOfAttributeMappings()];
        for (int i = 0; i < embeddings.size(); i++) {
            values[embeddingAttributeMapping.getStateArrayPosition()] =
                    embeddings.get(i).vector();

            if (embedded != null && embedded.get(i) != null) {
                if (embeddedTextAttributeMapping != null) {
                    values[embeddedTextAttributeMapping.getStateArrayPosition()] =
                            embedded.get(i).text();
                }
                final Map<String, String> metadataMap =
                        toStringValueMap(embedded.get(i).metadata().toMap());
                for (Map.Entry<String, AttributeMapping> entry : metadataAttributeMappings.entrySet()) {
                    final String attributePath = entry.getKey();
                    final String stringValue = metadataMap.remove(attributePath);
                    final Object value = stringValue == null
                            ? null
                            : entry.getValue().getJavaType().fromString(stringValue);
                    if (entry.getValue().getDeclaringType() != entityPersister) {
                        if (value != null) {
                            throw new IllegalArgumentException(
                                    "Can't add metadata from TextSegment for attribute path: " + attributePath);
                        }
                    } else {
                        values[entry.getValue().getStateArrayPosition()] = value;
                    }
                }
                if (unmappedMetadataAttributeMapType != null) {
                    values[unmappedMetadataAttributeMapping.getStateArrayPosition()] = metadataMap;
                } else {
                    try {
                        values[unmappedMetadataAttributeMapping.getStateArrayPosition()] =
                                OBJECT_MAPPER.writeValueAsString(metadataMap);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                if (embeddedTextAttributeMapping != null) {
                    values[embeddedTextAttributeMapping.getStateArrayPosition()] = null;
                }
                values[unmappedMetadataAttributeMapping.getStateArrayPosition()] = null;
                for (Map.Entry<String, AttributeMapping> entry : metadataAttributeMappings.entrySet()) {
                    if (entry.getValue().getDeclaringType() != entityPersister) {
                        values[entry.getValue().getStateArrayPosition()] = null;
                    }
                }
            }

            final Object entity = instantiator.instantiate();
            entityPersister.setValues(entity, values);
            entities.add(entity);
        }
        return entities;
    }

    private void addAll(
            List<Object> ids, List<Embedding> embeddings, List<TextSegment> embedded, StatelessSession session) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");
        if (!idGenerator.allowAssignedIdentifiers()) {
            throw new IllegalStateException("Entity does not allow assigning identifiers");
        }

        // todo: use new query batching API?
        final MutationQuery mutationQuery = session.createMutationQuery(insertValues);
        for (int i = 0; i < ids.size(); ++i) {
            mutationQuery.setParameter(idAttributeMapping.getAttributeName(), ids.get(i));
            mutationQuery.setParameter(
                    embeddingAttributeMapping.getAttributeName(),
                    embeddings.get(i).vector());

            if (embedded != null && embedded.get(i) != null) {
                if (embeddedTextAttributeMapping != null) {
                    mutationQuery.setParameter(
                            embeddedTextAttributeMapping.getAttributeName(),
                            embedded.get(i).text());
                }
                final Map<String, String> metadataMap =
                        toStringValueMap(embedded.get(i).metadata().toMap());
                for (Map.Entry<String, AttributeMapping> entry : metadataAttributeMappings.entrySet()) {
                    final String attributePath = entry.getKey();
                    final String stringValue = metadataMap.remove(attributePath);
                    final Object value = stringValue == null
                            ? null
                            : entry.getValue().getJavaType().fromString(stringValue);
                    mutationQuery.setParameter(attributePath, value);
                }
                if (unmappedMetadataAttributeMapType != null) {
                    mutationQuery.setParameter(
                            unmappedMetadataAttributeMapping.getAttributeName(),
                            metadataMap,
                            unmappedMetadataAttributeMapType);
                } else {
                    try {
                        mutationQuery.setParameter(
                                unmappedMetadataAttributeMapping.getAttributeName(),
                                OBJECT_MAPPER.writeValueAsString(metadataMap));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                if (embeddedTextAttributeMapping != null) {
                    mutationQuery.setParameter(embeddedTextAttributeMapping.getAttributeName(), null);
                }
                mutationQuery.setParameter(unmappedMetadataAttributeMapping.getAttributeName(), null);
                for (String attributePath : metadataAttributeMappings.keySet()) {
                    mutationQuery.setParameter(attributePath, null);
                }
            }
            mutationQuery.executeUpdate();
        }
    }

    public static class Builder<E> {
        private final Class<E> entityClass;
        private String embeddingAttributeName;
        private String embeddedTextAttributeName;
        private String unmappedMetadataAttributeName;
        private String[] metadataAttributeNames;
        private SessionFactory sessionFactory;
        private DatabaseKind databaseKind;
        private DistanceFunction distanceFunction = DistanceFunction.COSINE;

        Builder(Class<E> entityClass) {
            this.entityClass = entityClass;
        }

        public Builder<E> embeddingAttributeName(String embeddingAttributeName) {
            this.embeddingAttributeName = embeddingAttributeName;
            return this;
        }

        public Builder<E> embeddedTextAttributeName(String embeddedTextAttributeName) {
            this.embeddedTextAttributeName = embeddedTextAttributeName;
            return this;
        }

        public Builder<E> unmappedMetadataAttributeName(String unmappedMetadataAttributeName) {
            this.unmappedMetadataAttributeName = unmappedMetadataAttributeName;
            return this;
        }

        public Builder<E> metadataAttributeNames(String... metadataAttributeNames) {
            this.metadataAttributeNames = metadataAttributeNames;
            return this;
        }

        public Builder<E> sessionFactory(SessionFactory sessionFactory) {
            this.sessionFactory = sessionFactory;
            return this;
        }

        public Builder<E> databaseKind(DatabaseKind databaseKind) {
            this.databaseKind = databaseKind;
            return this;
        }

        public Builder<E> distanceFunction(DistanceFunction distanceFunction) {
            this.distanceFunction = ensureNotNull(distanceFunction, "distanceFunction");
            return this;
        }

        public HibernateEmbeddingStore<E> build() {
            final String embeddingAttributeName;
            final String embeddedTextAttributeName;
            final String unmappedMetadataAttributeName;
            final String[] metadataAttributeNames;
            if (this.embeddingAttributeName == null
                    || this.embeddedTextAttributeName == null
                    || this.unmappedMetadataAttributeName == null
                    || this.metadataAttributeNames == null) {
                final EntityType<?> entityType = sessionFactory.getMetamodel().entity(entityClass);
                SingularAttribute<?, ?> embeddingAttribute = null;
                SingularAttribute<?, ?> embeddedTextAttribute = null;
                SingularAttribute<?, ?> unmappedMetadataAttribute = null;
                LinkedHashSet<String> metadataAttributes = new LinkedHashSet<>();
                for (SingularAttribute<?, ?> singularAttribute : entityType.getSingularAttributes()) {
                    final Member member = singularAttribute.getJavaMember();
                    if (member instanceof AnnotatedElement annotatedElement) {
                        if (annotatedElement.isAnnotationPresent(
                                dev.langchain4j.store.embedding.hibernate.Embedding.class)) {
                            if (embeddingAttribute != null) {
                                throw new IllegalArgumentException("Multiple @Embedding annotated attributes ["
                                        + embeddingAttribute.getName() + "," + singularAttribute.getName()
                                        + "] found on " + entityClass.getName()
                                        + ". Please specify the explicit embedding attribute name instead");
                            }
                            embeddingAttribute = singularAttribute;
                        }
                        if (annotatedElement.isAnnotationPresent(EmbeddedText.class)) {
                            if (embeddedTextAttribute != null) {
                                throw new IllegalArgumentException("Multiple @EmbeddedText annotated attributes ["
                                        + embeddedTextAttribute.getName() + "," + singularAttribute.getName()
                                        + "] found on " + entityClass.getName()
                                        + ". Please specify the explicit embedded text attribute name instead");
                            }
                            embeddedTextAttribute = singularAttribute;
                        }
                        if (annotatedElement.isAnnotationPresent(UnmappedMetadata.class)) {
                            if (unmappedMetadataAttribute != null) {
                                throw new IllegalArgumentException("Multiple @UnmappedMetadata annotated attributes ["
                                        + unmappedMetadataAttribute.getName() + "," + singularAttribute.getName()
                                        + "] found on " + entityClass.getName()
                                        + ". Please specify the explicit unmapped metadata attribute name instead");
                            }
                            unmappedMetadataAttribute = singularAttribute;
                        }
                        if (annotatedElement.isAnnotationPresent(MetadataAttribute.class)) {
                            Set<ManagedType<?>> visitedTypes = new HashSet<>();
                            visitedTypes.add(entityType);
                            collectMetadataAttributes(
                                    visitedTypes,
                                    metadataAttributes,
                                    singularAttribute.getName(),
                                    singularAttribute.getType());
                        }
                    }
                }
                if (embeddingAttribute == null) {
                    throw new IllegalArgumentException("Embedding attribute not found on " + entityClass.getName()
                            + ". Did you forget to annotate @Embedding on an attribute?");
                }
                if (unmappedMetadataAttribute == null) {
                    throw new IllegalArgumentException("Text metadata attribute not found on " + entityClass.getName()
                            + ". Did you forget to annotate @UnmappedMetadata on an attribute?");
                }
                embeddingAttributeName = embeddingAttribute.getName();
                embeddedTextAttributeName = embeddedTextAttribute == null ? null : embeddedTextAttribute.getName();
                unmappedMetadataAttributeName = unmappedMetadataAttribute.getName();
                metadataAttributeNames = metadataAttributes.toArray(new String[0]);
            } else {
                embeddingAttributeName = this.embeddingAttributeName;
                embeddedTextAttributeName = this.embeddedTextAttributeName;
                unmappedMetadataAttributeName = this.unmappedMetadataAttributeName;
                metadataAttributeNames = this.metadataAttributeNames;
            }
            final DatabaseKind databaseKind;
            if (this.databaseKind == null) {
                databaseKind = DatabaseKind.determineDatabaseKind(
                        sessionFactory.unwrap(JdbcServices.class).getDialect());
                if (databaseKind == null) {
                    throw new IllegalArgumentException(
                            "Could not determine DatabaseKind based on dialect. Please configure it explicitly");
                }
            } else {
                databaseKind = this.databaseKind;
            }
            return new HibernateEmbeddingStore<>(
                    false,
                    this.sessionFactory,
                    databaseKind,
                    this.entityClass,
                    embeddingAttributeName,
                    embeddedTextAttributeName,
                    unmappedMetadataAttributeName,
                    metadataAttributeNames,
                    this.distanceFunction);
        }

        private void collectMetadataAttributes(
                Set<ManagedType<?>> visitedTypes, LinkedHashSet<String> metadataAttributes, String path, Type<?> type) {
            if (type instanceof ManagedType<?> managedType) {
                if (visitedTypes.add(managedType)) {
                    for (SingularAttribute<?, ?> attribute : managedType.getSingularAttributes()) {
                        if (attribute.getJavaMember() instanceof AnnotatedElement annotatedElement
                                && annotatedElement.isAnnotationPresent(MetadataAttribute.class)) {
                            collectMetadataAttributes(
                                    visitedTypes,
                                    metadataAttributes,
                                    path + "." + attribute.getName(),
                                    attribute.getType());
                            visitedTypes.remove(managedType);
                        }
                    }
                } else {
                    // todo: error when metadata annotations can lead to a cycle?
                }
            } else {
                metadataAttributes.add(path);
            }
        }

        public String toString() {
            return "HibernateEmbeddingStore.HibernateEmbeddingStoreBuilder(sessionFactory=" + this.sessionFactory
                    + ", databaseKind=" + this.databaseKind
                    + ", entityClass=" + this.entityClass.getName()
                    + ", embeddingAttributeName=" + this.embeddingAttributeName
                    + ", embeddedTextAttributeName=" + this.embeddedTextAttributeName
                    + ", unmappedMetadataAttributeName=" + this.unmappedMetadataAttributeName
                    + ", metadataAttributeNames=" + Arrays.toString(this.metadataAttributeNames)
                    + ")";
        }
    }

    public static class BaseBuilder<E> {
        protected DatabaseKind databaseKind;
        protected String table;
        protected Integer dimension;
        protected Boolean createIndex;
        protected String indexType;
        protected String indexOptions;
        protected Boolean createTable;
        protected Boolean dropTableFirst;
        protected DistanceFunction distanceFunction = DistanceFunction.COSINE;

        protected Configuration createConfiguration() {
            final int dimension = ensureNotNull(this.dimension, "dimension");
            final Configuration cfg = new Configuration(new BootstrapServiceRegistryBuilder()
                    .applyClassLoaderService(new ClassLoaderServiceImpl() {
                        @Override
                        public <S> Collection<S> loadJavaServices(final Class<S> serviceContract) {
                            //noinspection unchecked
                            return serviceContract == AdditionalMappingContributor.class
                                    ? List.of((S) new DynamicEmbeddingStoreAdditionalMappingContributor(dimension))
                                    // todo: Maybe return an empty list otherwise? Does it make sense to let classes
                                    // creep in?
                                    : super.loadJavaServices(serviceContract);
                        }
                    })
                    .build());
            final boolean drop = getOrDefault(dropTableFirst, false);
            final boolean create = getOrDefault(createTable, false);
            if (drop && create) {
                cfg.setSchemaExportAction(Action.CREATE);
            } else if (drop) {
                // Does this make sense?
                cfg.setSchemaExportAction(Action.DROP);
            } else if (create) {
                cfg.setSchemaExportAction(Action.CREATE_ONLY);
            } else {
                cfg.setSchemaExportAction(Action.POPULATE);
            }
            final String ormXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<entity-mappings xmlns=\"https://www.hibernate.org/xsd/orm/mapping\" version=\"7.0\">\n"
                    + "	<package>dev.langchain4j.store.embedding.hibernate</package>\n"
                    + "    <entity class=\"EmbeddingEntity\">\n"
                    + "        <table name=\""
                    + ensureNotBlank(table, "table") + "\"/>\n" + "    </entity>\n"
                    + "</entity-mappings>";
            cfg.addInputStream(new ReaderInputStream(new StringReader(ormXmlContent)));
            return cfg;
        }

        protected SessionFactory createSessionFactory(Configuration cfg, DatabaseKind databaseKind) {
            // Builder for dynamic mapping use case for people who just want to use it without mapping an entity,
            // based on orm.xml/hbm.xml, because we need to allow overriding the vector dimension programmatically
            final String setupSql = databaseKind.getSetupSql();
            if (setupSql != null) {
                cfg.setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE, SourceType.SCRIPT_THEN_METADATA);
                cfg.getProperties()
                        .put(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE, new StringReader(setupSql));
            }
            final boolean index = getOrDefault(createIndex, false);
            final String importSqlContent = index
                    ? databaseKind.createIndexDDL(distanceFunction, indexType, table, "embedding", indexOptions)
                    : null;
            // Always set this to avoid a default file to creep in
            cfg.getProperties()
                    .put(
                            SchemaToolingSettings.JAKARTA_HBM2DDL_LOAD_SCRIPT_SOURCE,
                            new ReaderInputStream(new StringReader(importSqlContent == null ? "" : importSqlContent)));
            return cfg.buildSessionFactory();
        }

        public BaseBuilder<E> databaseKind(DatabaseKind databaseKind) {
            this.databaseKind = databaseKind;
            return this;
        }

        public BaseBuilder<E> table(String table) {
            this.table = table;
            return this;
        }

        public BaseBuilder<E> dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public BaseBuilder<E> createIndex(Boolean createIndex) {
            this.createIndex = createIndex;
            return this;
        }

        public BaseBuilder<E> indexType(String indexType) {
            this.indexType = indexType;
            return this;
        }

        public BaseBuilder<E> indexOptions(String indexOptions) {
            this.indexOptions = indexOptions;
            return this;
        }

        public BaseBuilder<E> createTable(Boolean createTable) {
            this.createTable = createTable;
            return this;
        }

        public BaseBuilder<E> dropTableFirst(Boolean dropTableFirst) {
            this.dropTableFirst = dropTableFirst;
            return this;
        }

        public BaseBuilder<E> distanceFunction(DistanceFunction distanceFunction) {
            this.distanceFunction = ensureNotNull(distanceFunction, "distanceFunction");
            return this;
        }
    }

    public static class DynamicBuilder extends BaseBuilder<EmbeddingEntity> {
        private String host;
        private int port;
        private String database;
        private String jdbcUrl;
        private String user;
        private String password;

        DynamicBuilder() {}

        public DynamicBuilder host(String host) {
            this.host = host;
            return this;
        }

        public DynamicBuilder port(int port) {
            this.port = port;
            return this;
        }

        public DynamicBuilder database(String database) {
            this.database = database;
            return this;
        }

        public DynamicBuilder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }

        public DynamicBuilder user(String user) {
            this.user = user;
            return this;
        }

        public DynamicBuilder password(String password) {
            this.password = password;
            return this;
        }

        @Override
        public DynamicBuilder databaseKind(DatabaseKind databaseKind) {
            super.databaseKind(databaseKind);
            return this;
        }

        @Override
        public DynamicBuilder table(String table) {
            super.table(table);
            return this;
        }

        @Override
        public DynamicBuilder dimension(Integer dimension) {
            super.dimension(dimension);
            return this;
        }

        @Override
        public DynamicBuilder createIndex(Boolean createIndex) {
            super.createIndex(createIndex);
            return this;
        }

        @Override
        public DynamicBuilder indexType(final String indexType) {
            super.indexType(indexType);
            return this;
        }

        @Override
        public DynamicBuilder indexOptions(String indexOptions) {
            super.indexOptions(indexOptions);
            return this;
        }

        @Override
        public DynamicBuilder createTable(Boolean createTable) {
            super.createTable(createTable);
            return this;
        }

        @Override
        public DynamicBuilder dropTableFirst(Boolean dropTableFirst) {
            super.dropTableFirst(dropTableFirst);
            return this;
        }

        @Override
        public DynamicBuilder distanceFunction(DistanceFunction distanceFunction) {
            super.distanceFunction(distanceFunction);
            return this;
        }

        public HibernateEmbeddingStore<EmbeddingEntity> build() {
            final Configuration cfg = createConfiguration();
            final DatabaseKind databaseKind;
            if (isNullOrBlank(jdbcUrl)) {
                databaseKind = ensureNotNull(this.databaseKind, "databaseKind");
                final String jdbcUrl = databaseKind.createJdbcUrl(
                        ensureNotBlank(host, "host"), port, ensureNotBlank(database, "database"));
                cfg.setProperty(JdbcSettings.JAKARTA_JDBC_URL, jdbcUrl);
            } else {
                final String jdbcUrl = ensureNotBlank(this.jdbcUrl, "jdbcUrl");
                databaseKind = DatabaseKind.determineDatabaseKind(jdbcUrl);
                if (databaseKind == null) {
                    throw new IllegalArgumentException("Can't determine DatabaseKind for JDBC URL: " + jdbcUrl);
                }
                cfg.setProperty(JdbcSettings.JAKARTA_JDBC_URL, jdbcUrl);
            }
            cfg.setProperty(JdbcSettings.JAKARTA_JDBC_USER, ensureNotBlank(user, "user"));
            cfg.setProperty(JdbcSettings.JAKARTA_JDBC_PASSWORD, ensureNotBlank(password, "password"));
            return new HibernateEmbeddingStore<>(
                    true,
                    createSessionFactory(cfg, databaseKind),
                    databaseKind,
                    EmbeddingEntity.class,
                    "embedding",
                    "text",
                    "metadata",
                    null,
                    distanceFunction);
        }

        public String toString() {
            return "HibernateEmbeddingStore.DynamicBuilder(jdbcUrl=" + this.jdbcUrl
                    + ", databaseKind=" + this.databaseKind
                    + ", user=" + this.user
                    + ", password=" + this.password
                    + ", table=" + this.table
                    + ", dimension=" + this.dimension
                    + ", createIndex=" + this.createIndex
                    + ", indexType=" + this.indexType
                    + ", indexOptions=(" + this.indexOptions + ")"
                    + ", createTable=" + this.createTable
                    + ", dropTableFirst=" + this.dropTableFirst
                    + ", distanceFunction=" + this.distanceFunction
                    + ")";
        }
    }

    public static class DynamicDatasourceBuilder extends BaseBuilder<EmbeddingEntity> {
        private DataSource dataSource;

        DynamicDatasourceBuilder() {}

        public DynamicDatasourceBuilder dataSource(DataSource datasource) {
            this.dataSource = datasource;
            return this;
        }

        @Override
        public DynamicDatasourceBuilder databaseKind(DatabaseKind databaseKind) {
            super.databaseKind(databaseKind);
            return this;
        }

        @Override
        public DynamicDatasourceBuilder table(String table) {
            super.table(table);
            return this;
        }

        @Override
        public DynamicDatasourceBuilder dimension(Integer dimension) {
            super.dimension(dimension);
            return this;
        }

        @Override
        public DynamicDatasourceBuilder createIndex(Boolean createIndex) {
            super.createIndex(createIndex);
            return this;
        }

        @Override
        public DynamicDatasourceBuilder indexType(String indexType) {
            super.indexType(indexType);
            return this;
        }

        @Override
        public DynamicDatasourceBuilder indexOptions(String indexOptions) {
            super.indexOptions(indexOptions);
            return this;
        }

        @Override
        public DynamicDatasourceBuilder createTable(Boolean createTable) {
            super.createTable(createTable);
            return this;
        }

        @Override
        public DynamicDatasourceBuilder dropTableFirst(Boolean dropTableFirst) {
            super.dropTableFirst(dropTableFirst);
            return this;
        }

        @Override
        public DynamicDatasourceBuilder distanceFunction(DistanceFunction distanceFunction) {
            super.distanceFunction(distanceFunction);
            return this;
        }

        public HibernateEmbeddingStore<EmbeddingEntity> build() {
            final Configuration cfg = createConfiguration();
            cfg.getProperties().put(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE, ensureNotNull(dataSource, "dataSource"));
            final DatabaseKind databaseKind = ensureNotNull(this.databaseKind, "databaseKind");
            return new HibernateEmbeddingStore<>(
                    true,
                    createSessionFactory(cfg, databaseKind),
                    databaseKind,
                    EmbeddingEntity.class,
                    "embedding",
                    "text",
                    "metadata",
                    null,
                    distanceFunction);
        }

        public String toString() {
            return "HibernateEmbeddingStore.DynamicDatasourceBuilder(datasource=" + this.dataSource
                    + ", databaseKind=" + this.databaseKind
                    + ", table=" + this.table
                    + ", dimension=" + this.dimension
                    + ", createIndex=" + this.createIndex
                    + ", indexType=" + this.indexType
                    + ", indexOptions=(" + this.indexOptions + ")"
                    + ", createTable=" + this.createTable
                    + ", dropTableFirst=" + this.dropTableFirst
                    + ", distanceFunction=" + this.distanceFunction
                    + ")";
        }
    }

    private static class DynamicEmbeddingStoreAdditionalMappingContributor implements AdditionalMappingContributor {

        private final int dimension;

        public DynamicEmbeddingStoreAdditionalMappingContributor(final int dimension) {
            this.dimension = dimension;
        }

        @Override
        public void contribute(
                final AdditionalMappingContributions contributions,
                final InFlightMetadataCollector metadata,
                final ResourceStreamLocator resourceStreamLocator,
                final MetadataBuildingContext buildingContext) {
            metadata.getEntityBinding(EmbeddingEntity.class.getName())
                    .getProperty("embedding")
                    .getValue()
                    .getColumns()
                    .get(0)
                    .setArrayLength(dimension);
        }

        @Override
        public String getContributorName() {
            return "Langchain4j Hibernate DynamicEmbeddingStore";
        }
    }
}
