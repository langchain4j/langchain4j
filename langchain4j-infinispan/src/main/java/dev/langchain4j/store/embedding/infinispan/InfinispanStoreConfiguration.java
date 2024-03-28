package dev.langchain4j.store.embedding.infinispan;

/**
 * Holds configuration for the store
 */
public record InfinispanStoreConfiguration(String cacheName,
                                           Integer dimension,
                                           Integer distance,
                                           String similarity,
                                           String cacheConfig,
                                           String packageItem,
                                           String fileName,
                                           String langchainItemName,
                                           String metadataItemName,
                                           boolean createCache,
                                           boolean registerSchema){

   /**
    * Default Cache Config
    */
   public static final String DEFAULT_CACHE_CONFIG =
         "<distributed-cache name=\"CACHE_NAME\">\n"
               + "<indexing storage=\"local-heap\">\n"
               + "<indexed-entities>\n"
               + "<indexed-entity>LANGCHAINITEM</indexed-entity>\n"
               + "<indexed-entity>LANGCHAIN_METADATA</indexed-entity>\n"
               + "</indexed-entities>\n"
               + "</indexing>\n"
               + "</distributed-cache>";

   /**
    * Default package of the schema
    */
   public static final String DEFAULT_ITEM_PACKAGE = "dev.langchain4j";

   /**
    * Default name of the protobuf langchain item. Size will be added
    */
   public static final String DEFAULT_LANGCHAIN_ITEM = "LangChainItem";
   /**
    * Default name of the protobuf metadata item. Size will be added
    */
   public static final String DEFAULT_METADATA_ITEM = "LangChainMetadata";
   /**
    * The default distance to for the search
    */
   public static final int DEFAULT_DISTANCE = 3;
   /**
    * Default vector similarity
    */
   public static final String DEFAULT_SIMILARITY = "COSINE";

   /**
    * Creates the configuration and sets default values
    *
    * @param cacheName, mandatory
    * @param dimension, mandatory
    * @param distance, defaults to 3
    * @param similarity, defaults COUSINE
    * @param cacheConfig, the full cache configuration
    * @param packageItem, optional the package item
    * @param fileName, optional file name
    * @param langchainItemName, optional item name
    * @param metadataItemName, optional metadata item name
    * @param createCache, defaults to true. Disables creating the cache on startup
    * @param registerSchema, defaults to true. Disables registering the schema in the server
    */
   public InfinispanStoreConfiguration(String cacheName, Integer dimension, Integer distance, String similarity, String cacheConfig,
                                       String packageItem, String fileName, String langchainItemName,
                                       String metadataItemName, boolean createCache, boolean registerSchema) {
      this.cacheName = cacheName;
      this.dimension = dimension;
      this.cacheConfig = cacheConfig;
      this.distance = distance != null ? distance : DEFAULT_DISTANCE;
      this.similarity = similarity != null ? similarity : DEFAULT_SIMILARITY;
      this.packageItem = packageItem != null ? packageItem : DEFAULT_ITEM_PACKAGE;
      this.fileName = fileName != null ? fileName: computeFileName(packageItem, dimension);
      this.langchainItemName = langchainItemName != null? langchainItemName : DEFAULT_LANGCHAIN_ITEM + dimension;
      this.metadataItemName = metadataItemName != null? metadataItemName : DEFAULT_METADATA_ITEM + dimension;
      this.createCache = createCache;
      this.registerSchema = registerSchema;
   }

   /**
    * Get the full name of the langchainItem protobuf type
    * @return langchainItemFullType
    */
   public String langchainItemFullType() {
      return packageItem + "." + langchainItemName;
   }

   /**
    * Get the full name of the metadata protobuf type
    * @return metadataFullType
    */
   public String metadataFullType() {
      return packageItem + "." + metadataItemName;
   }

   private static String computeFileName(String itemPackage, int dimension) {
      return itemPackage + "." + "dimension." + dimension + ".proto";
   }

}
