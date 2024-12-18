package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiModelPermission {
    private String id;
    private String object;
    private Integer created;
    private Boolean allowCreateEngine;
    private Boolean allowSampling;
    private Boolean allowLogprobs;
    private Boolean allowSearchIndices;
    private Boolean allowView;
    private Boolean allowFineTuning;
    private String organization;
    private String group;
    private Boolean isBlocking;

    public static class MistralAiModelPermissionBuilder {

        private String id;

        private String object;

        private Integer created;

        private Boolean allowCreateEngine;

        private Boolean allowSampling;

        private Boolean allowLogprobs;

        private Boolean allowSearchIndices;

        private Boolean allowView;

        private Boolean allowFineTuning;

        private String organization;

        private String group;

        private Boolean isBlocking;

        MistralAiModelPermissionBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder object(String object) {
            this.object = object;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder created(Integer created) {
            this.created = created;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder allowCreateEngine(Boolean allowCreateEngine) {
            this.allowCreateEngine = allowCreateEngine;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder allowSampling(Boolean allowSampling) {
            this.allowSampling = allowSampling;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder allowLogprobs(Boolean allowLogprobs) {
            this.allowLogprobs = allowLogprobs;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder allowSearchIndices(Boolean allowSearchIndices) {
            this.allowSearchIndices = allowSearchIndices;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder allowView(Boolean allowView) {
            this.allowView = allowView;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder allowFineTuning(Boolean allowFineTuning) {
            this.allowFineTuning = allowFineTuning;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder organization(String organization) {
            this.organization = organization;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder group(String group) {
            this.group = group;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelPermission.MistralAiModelPermissionBuilder isBlocking(Boolean isBlocking) {
            this.isBlocking = isBlocking;
            return this;
        }

        public MistralAiModelPermission build() {
            return new MistralAiModelPermission(
                    this.id,
                    this.object,
                    this.created,
                    this.allowCreateEngine,
                    this.allowSampling,
                    this.allowLogprobs,
                    this.allowSearchIndices,
                    this.allowView,
                    this.allowFineTuning,
                    this.organization,
                    this.group,
                    this.isBlocking);
        }

        public String toString() {
            return "MistralAiModelPermission.MistralAiModelPermissionBuilder("
                    + "id=" + this.id
                    + ", object=" + this.object
                    + ", created=" + this.created
                    + ", allowCreateEngine=" + this.allowCreateEngine
                    + ", allowSampling=" + this.allowSampling
                    + ", allowLogprobs=" + this.allowLogprobs
                    + ", allowSearchIndices=" + this.allowSearchIndices
                    + ", allowView=" + this.allowView
                    + ", allowFineTuning=" + this.allowFineTuning
                    + ", organization=" + this.organization
                    + ", group=" + this.group
                    + ", isBlocking=" + this.isBlocking
                    + ")";
        }
    }

    public static MistralAiModelPermission.MistralAiModelPermissionBuilder builder() {
        return new MistralAiModelPermission.MistralAiModelPermissionBuilder();
    }

    public String getId() {
        return this.id;
    }

    public String getObject() {
        return this.object;
    }

    public Integer getCreated() {
        return this.created;
    }

    public Boolean getAllowCreateEngine() {
        return this.allowCreateEngine;
    }

    public Boolean getAllowSampling() {
        return this.allowSampling;
    }

    public Boolean getAllowLogprobs() {
        return this.allowLogprobs;
    }

    public Boolean getAllowSearchIndices() {
        return this.allowSearchIndices;
    }

    public Boolean getAllowView() {
        return this.allowView;
    }

    public Boolean getAllowFineTuning() {
        return this.allowFineTuning;
    }

    public String getOrganization() {
        return this.organization;
    }

    public String getGroup() {
        return this.group;
    }

    public Boolean getIsBlocking() {
        return this.isBlocking;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public void setCreated(Integer created) {
        this.created = created;
    }

    public void setAllowCreateEngine(Boolean allowCreateEngine) {
        this.allowCreateEngine = allowCreateEngine;
    }

    public void setAllowSampling(Boolean allowSampling) {
        this.allowSampling = allowSampling;
    }

    public void setAllowLogprobs(Boolean allowLogprobs) {
        this.allowLogprobs = allowLogprobs;
    }

    public void setAllowSearchIndices(Boolean allowSearchIndices) {
        this.allowSearchIndices = allowSearchIndices;
    }

    public void setAllowView(Boolean allowView) {
        this.allowView = allowView;
    }

    public void setAllowFineTuning(Boolean allowFineTuning) {
        this.allowFineTuning = allowFineTuning;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setIsBlocking(Boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + Objects.hashCode(this.id);
        hash = 61 * hash + Objects.hashCode(this.object);
        hash = 61 * hash + Objects.hashCode(this.created);
        hash = 61 * hash + Objects.hashCode(this.allowCreateEngine);
        hash = 61 * hash + Objects.hashCode(this.allowSampling);
        hash = 61 * hash + Objects.hashCode(this.allowLogprobs);
        hash = 61 * hash + Objects.hashCode(this.allowSearchIndices);
        hash = 61 * hash + Objects.hashCode(this.allowView);
        hash = 61 * hash + Objects.hashCode(this.allowFineTuning);
        hash = 61 * hash + Objects.hashCode(this.organization);
        hash = 61 * hash + Objects.hashCode(this.group);
        hash = 61 * hash + Objects.hashCode(this.isBlocking);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiModelPermission other = (MistralAiModelPermission) obj;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.object, other.object)
                && Objects.equals(this.organization, other.organization)
                && Objects.equals(this.group, other.group)
                && Objects.equals(this.created, other.created)
                && Objects.equals(this.allowCreateEngine, other.allowCreateEngine)
                && Objects.equals(this.allowSampling, other.allowSampling)
                && Objects.equals(this.allowLogprobs, other.allowLogprobs)
                && Objects.equals(this.allowSearchIndices, other.allowSearchIndices)
                && Objects.equals(this.allowView, other.allowView)
                && Objects.equals(this.allowFineTuning, other.allowFineTuning)
                && Objects.equals(this.isBlocking, other.isBlocking);
    }

    public String toString() {
        return "MistralAiModelPermission("
                + "id=" + this.getId()
                + ", object=" + this.getObject()
                + ", created=" + this.getCreated()
                + ", allowCreateEngine=" + this.getAllowCreateEngine()
                + ", allowSampling=" + this.getAllowSampling()
                + ", allowLogprobs=" + this.getAllowLogprobs()
                + ", allowSearchIndices=" + this.getAllowSearchIndices()
                + ", allowView=" + this.getAllowView()
                + ", allowFineTuning=" + this.getAllowFineTuning()
                + ", organization=" + this.getOrganization()
                + ", group=" + this.getGroup()
                + ", isBlocking=" + this.getIsBlocking()
                + ")";
    }

    public MistralAiModelPermission() {}

    public MistralAiModelPermission(
            String id,
            String object,
            Integer created,
            Boolean allowCreateEngine,
            Boolean allowSampling,
            Boolean allowLogprobs,
            Boolean allowSearchIndices,
            Boolean allowView,
            Boolean allowFineTuning,
            String organization,
            String group,
            Boolean isBlocking) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.allowCreateEngine = allowCreateEngine;
        this.allowSampling = allowSampling;
        this.allowLogprobs = allowLogprobs;
        this.allowSearchIndices = allowSearchIndices;
        this.allowView = allowView;
        this.allowFineTuning = allowFineTuning;
        this.organization = organization;
        this.group = group;
        this.isBlocking = isBlocking;
    }
}
