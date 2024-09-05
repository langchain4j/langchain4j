package dev.langchain4j.store.embedding.oracle;

import static dev.langchain4j.internal.ValidationUtils.*;

/**
 * <p>
 *   This index type can be used to index the embedding column of the
 *   {@link EmbeddingTable}.
 * </p><p>
 *   <em>Inverted File Flat (IVF)</em>:  index is the only type of Neighbor Partition
 *   vector index supported. Inverted File Flat Index (IVF Flat or simply IVF) is a
 *   partitioned-based index which balance high search quality with reasonable speed.
 * </p><p>
 *   The {@link Builder} allows to configure the IVF index.
 * </p>
 */
public class IVFIndex extends Index {

  IVFIndex(CreateOption createOption, String createIndexStatement, String dropIndexStatement) {
    super(createOption, createIndexStatement, dropIndexStatement);
  }

  /**
   * Returns a builder that configures a new IVF index.
   *
   * @return An IVFIndex builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * An {@Link IndexBuilder} that builds a IVFIndex.
   */
  public static class Builder extends IndexBuilder {

    /**
     * Suffix used when naming the index.
     */
    private static final String INDEX_NAME_SUFIX = "_VECTOR_INDEX";

    private String indexName;

    /**
     * CreateOption for the index. By default, the index will not be created.
     */
    private CreateOption createOption = CreateOption.DO_NOT_CREATE;

    protected int targetAccuracy = -1;

    protected int degreeOfParallelism = -1;

    private int neighborPartitions = -1;

    private int samplePerPartition = -1;

    private int minVectorsPerPartition = -1;

    /**
     * Configures the option to create (or not create) an index. The default is
     * {@link CreateOption#DO_NOT_CREATE}, which means no attempt is made to create
     *  an index.
     *
     * @param createOption The create option.
     *
     * @return This builder.
     */
    public Builder createOption(CreateOption createOption) {
      ensureNotNull(createOption, "createOption");
      this.createOption = createOption;
      return this;
    }

    /**
     * Sets the index name.
     * @param indexName The name of the index.
     * @return This builder.
     */
    public Builder name(String indexName) {
      this.indexName = indexName;
      return this;
    }

    /**
     * Configures the target accuracy.
     *
     * @param targetAccuracy Percentage value.
     * @return This builder.
     * @throws IllegalArgumentException If the target accuracy not between 1 and 100.
     */
    public Builder targetAccuracy(int targetAccuracy) throws IllegalArgumentException {
      ensureBetween(targetAccuracy, 0, 100, "targetAccuracy");
      this.targetAccuracy = targetAccuracy;
      return this;
    }

    /**
     * Configures the degree of parallelism of the index.
     *
     * @param degreeOfParallelism The degree of parallelism.
     * @return This builder.
     */
    public Builder degreeOfParallelism(int degreeOfParallelism) {
      ensureGreaterThanZero(degreeOfParallelism, "degreeOfParallelism");
      this.degreeOfParallelism = degreeOfParallelism;
      return this;
    }

    /**
     * Configures the number of neighbor partitions.
     * <p>
     * This is a IVF Specific Parameters. It  determines the number of centroid partitions that are
     * created by the index.
     * </p>
     *
     * @param neighborPartitions The number of neighbor partitions.
     * @return This builder.
     * @throws IllegalArgumentException If the number of neighbor partitions is not between 1 and
     *                                  10000000, or if the vector type is not IVF.
     */
    public Builder neighborPartitions(int neighborPartitions) throws IllegalArgumentException {
      ensureBetween(neighborPartitions, 1, 10000000, "neighborPartitions");
      this.neighborPartitions = neighborPartitions;
      return this;
    }

    /**
     * Configures the total number of vectors that are passed to the clustering algorithm.
     * <p>
     * This is a IVF Specific Parameters. It  decides the total number of vectors that are passed to
     * the clustering algorithm (number of samples per partition times the number of neighbor
     * partitions).
     * </p>
     * <p>
     * <em>Note,</em> that passing all the vectors would significantly increase the total time to
     * create the index. Instead, aim to pass a subset of vectors that can capture the data
     * distribution.
     * </p>
     *
     * @param samplePerPartition The total number of vectors that are passed to the clustering algorithm.
     * @return This builder.
     * @throws IllegalArgumentException If the number of samples per partition is lower than 1.
     */
    public Builder samplePerPartition(int samplePerPartition) throws IllegalArgumentException {
      ensureBetween(samplePerPartition, 1, Integer.MAX_VALUE, "samplePerPartition");
      this.samplePerPartition = samplePerPartition;
      return this;
    }

    /**
     * Configures the target minimum number of vectors per partition.
     * <p>
     * This is a IVF Specific Parameters. It represents the target minimum number of vectors per
     * partition. Aim to trim out any partition that can end up with fewer than 100 vectors. This
     * may result in lesser number of centroids. Its values can range from 0 (no trimming of
     * centroids) to num_vectors (would result in 1 neighbor partition).
     * </p>
     *
     * @param minVectorsPerPartition The target minimum number of vectors per partition.
     * @return This builder.
     * @throws IllegalArgumentException If the target minimum number of vectors per partition is lower
     *                                  than 0.
     */
    public Builder minVectorsPerPartition(int minVectorsPerPartition) throws IllegalArgumentException {
      ensureGreaterThanZero(minVectorsPerPartition, "minVectorsPerPartition");
      this.minVectorsPerPartition = minVectorsPerPartition;
      return this;
    }

    @Override
    public Index build(EmbeddingTable embeddingTable) {
      if (indexName == null) {
        indexName = buildIndexName(embeddingTable.name(), INDEX_NAME_SUFIX);
      }

      String createIndexStatement = "CREATE VECTOR INDEX " +
          (createOption == CreateOption.CREATE_IF_NOT_EXISTS ? "IF NOT EXISTS " : "") +
          indexName +
          " ON " + embeddingTable.name() + "( " + embeddingTable.embeddingColumn() + " ) " +
          " ORGANIZATION NEIGHBOR PARTITIONS " +
          " WITH DISTANCE COSINE " +
          (targetAccuracy > 0 ? " WITH TARGET ACCURACY " + targetAccuracy + " " : "") +
          (degreeOfParallelism >= 0 ? " PARALLEL " + degreeOfParallelism : "") +
          getIndexParameters();

      String dropIndexStatement = "DROP INDEX IF EXISTS " + indexName;
      return new IVFIndex(createOption, createIndexStatement, dropIndexStatement);
    }

     /**
     * Generates the PARAMETERS clause of the vector index. Implementation depends on the type of vector index.
     * @return A string containing the PARAMETERS clause of the index.
     */
    String getIndexParameters() {
      if (neighborPartitions == -1 && samplePerPartition == -1 && minVectorsPerPartition == -1) {
        return " ";
      }
      return "PARAMETERS ( TYPE IVF" +
          (neighborPartitions != -1 ? ", NEIGHBOR PARTITIONS " + neighborPartitions + " " : "") +
          (samplePerPartition != -1 ? ", SAMPLES_PER_PARTITION " + samplePerPartition + " " : "") +
          (minVectorsPerPartition != -1 ? ", MIN_VECTORS_PER_PARTITION " + minVectorsPerPartition + " " : "") + ")";
    }

  }
}
