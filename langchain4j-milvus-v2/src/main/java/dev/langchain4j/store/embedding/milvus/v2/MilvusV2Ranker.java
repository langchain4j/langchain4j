package dev.langchain4j.store.embedding.milvus.v2;

import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.vector.request.ranker.*;
import java.util.List;

public abstract class MilvusV2Ranker {

    abstract CreateCollectionReq.Function toFunction();

    public static MilvusV2Ranker rrf(int k) {
        return new MilvusV2Ranker() {
            @Override
            CreateCollectionReq.Function toFunction() {
                return RRFRanker.builder().k(k).build();
            }
        };
    }

    public static MilvusV2Ranker weighted(List<Float> weights) {
        return new MilvusV2Ranker() {
            @Override
            CreateCollectionReq.Function toFunction() {
                return WeightedRanker.builder().weights(weights).build();
            }
        };
    }

    public static BoostBuilder boost() {
        return new BoostBuilder();
    }

    public static class BoostBuilder {
        private final BoostRanker.BoostRankerBuilder builder = BoostRanker.builder();

        public BoostBuilder filter(String filter) {
            this.builder.filter(filter);
            return this;
        }

        public BoostBuilder weight(float weight) {
            this.builder.weight(weight);
            return this;
        }

        public BoostBuilder randomScoreSeed(long randomScoreSeed) {
            this.builder.randomScoreSeed(randomScoreSeed);
            return this;
        }

        public BoostBuilder randomScoreField(String randomScoreField) {
            this.builder.randomScoreField(randomScoreField);
            return this;
        }

        public MilvusV2Ranker build() {
            return new MilvusV2Ranker() {
                @Override
                CreateCollectionReq.Function toFunction() {
                    return builder.build();
                }
            };
        }
    }

    public static ModelRankerBuilder model() {
        return new ModelRankerBuilder();
    }

    public static class ModelRankerBuilder {
        private final ModelRanker.ModelRankerBuilder builder = ModelRanker.builder();

        public ModelRankerBuilder provider(String provider) {
            this.builder.provider(provider);
            return this;
        }

        public ModelRankerBuilder queries(List<String> queries) {
            this.builder.queries(queries);
            return this;
        }

        public ModelRankerBuilder endpoint(String endpoint) {
            this.builder.endpoint(endpoint);
            return this;
        }

        public MilvusV2Ranker build() {
            return new MilvusV2Ranker() {
                @Override
                CreateCollectionReq.Function toFunction() {
                    return builder.build();
                }
            };
        }
    }

    public static DecayBuilder decay() {
        return new DecayBuilder();
    }

    public static class DecayBuilder {
        private final DecayRanker.DecayRankerBuilder builder = DecayRanker.builder();

        public DecayBuilder function(String function) {
            this.builder.function(function);
            return this;
        }

        public DecayBuilder origin(Number origin) {
            this.builder.origin(origin);
            return this;
        }

        public DecayBuilder offset(Number offset) {
            this.builder.offset(offset);
            return this;
        }

        public DecayBuilder scale(Number scale) {
            this.builder.scale(scale);
            return this;
        }

        public DecayBuilder decay(Number decay) {
            this.builder.decay(decay);
            return this;
        }

        public MilvusV2Ranker build() {
            return new MilvusV2Ranker() {
                @Override
                CreateCollectionReq.Function toFunction() {
                    return builder.build();
                }
            };
        }
    }
}
