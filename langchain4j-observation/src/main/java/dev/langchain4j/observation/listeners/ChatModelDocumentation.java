package dev.langchain4j.observation.listeners;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.jspecify.annotations.Nullable;

public enum ChatModelDocumentation implements ObservationDocumentation {
    INSTANCE{
        @Override
        public @Nullable Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultChatModelConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityValues.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityValues.values();
        }
    };

    public enum LowCardinalityValues implements KeyName {
        OPERATION_NAME {
            @Override
            public String asString() {
                return "gen_ai.operation.name";
            }
        },
        PROVIDER_NAME {
            @Override
            public String asString() {
                return "gen_ai.provider.name";
            }
        },
        SYSTEM {
            @Override
            public String asString() {
                return "gen_ai.system";
            }
        },
        REQUEST_MODEL {
            @Override
            public String asString() {
                return "gen_ai.request.model";
            }
        },
        RESPONSE_MODEL {
            @Override
            public String asString() {
                return "gen_ai.response.model";
            }
        },
        TOKEN_TYPE {
            @Override
            public String asString() {
                return "gen_ai.token.type";
            }
        };
    }

    public enum HighCardinalityValues implements KeyName {
        TOKEN_USAGE {
            @Override
            public String asString() {
                return "gen_ai.client.token.usage";
            }
        };
    }


}
