package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiModelCard {
    private String id;
    private String object;
    private Integer created;
    private String ownerBy;
    private String root;
    private String parent;
    private List<MistralAiModelPermission> permission;

    public MistralAiModelCard() {}

    public MistralAiModelCard(MistralAiModelCardBuilder builder) {
        this.id = builder.id;
        this.object = builder.object;
        this.created = builder.created;
        this.ownerBy = builder.ownerBy;
        this.root = builder.root;
        this.parent = builder.parent;
        this.permission = builder.permission;
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

    public String getOwnerBy() {
        return this.ownerBy;
    }

    public String getRoot() {
        return this.root;
    }

    public String getParent() {
        return this.parent;
    }

    public List<MistralAiModelPermission> getPermission() {
        return this.permission;
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

    public void setOwnerBy(String ownerBy) {
        this.ownerBy = ownerBy;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public void setPermission(List<MistralAiModelPermission> permission) {
        this.permission = permission;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + Objects.hashCode(this.id);
        hash = 83 * hash + Objects.hashCode(this.object);
        hash = 83 * hash + Objects.hashCode(this.created);
        hash = 83 * hash + Objects.hashCode(this.ownerBy);
        hash = 83 * hash + Objects.hashCode(this.root);
        hash = 83 * hash + Objects.hashCode(this.parent);
        hash = 83 * hash + Objects.hashCode(this.permission);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiModelCard other = (MistralAiModelCard) obj;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.object, other.object)
                && Objects.equals(this.ownerBy, other.ownerBy)
                && Objects.equals(this.root, other.root)
                && Objects.equals(this.parent, other.parent)
                && Objects.equals(this.created, other.created)
                && Objects.equals(this.permission, other.permission);
    }

    @Override
    public String toString() {
        return "MistralAiModelCard("
                + "id=" + this.getId()
                + ", object=" + this.getObject()
                + ", created=" + this.getCreated()
                + ", ownerBy=" + this.getOwnerBy()
                + ", root=" + this.getRoot()
                + ", parent=" + this.getParent()
                + ", permission=" + this.getPermission()
                + ")";
    }

    public static MistralAiModelCardBuilder builder() {
        return new MistralAiModelCardBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiModelCardBuilder {
        private String id;
        private String object;
        private Integer created;
        private String ownerBy;
        private String root;
        private String parent;
        private List<MistralAiModelPermission> permission;

        private MistralAiModelCardBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiModelCardBuilder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelCardBuilder object(String object) {
            this.object = object;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelCardBuilder created(Integer created) {
            this.created = created;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelCardBuilder ownerBy(String ownerBy) {
            this.ownerBy = ownerBy;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelCardBuilder root(String root) {
            this.root = root;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelCardBuilder parent(String parent) {
            this.parent = parent;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelCardBuilder permission(List<MistralAiModelPermission> permission) {
            this.permission = permission;
            return this;
        }

        public MistralAiModelCard build() {
            return new MistralAiModelCard(this);
        }

        @Override
        public String toString() {
            return "MistralAiModelCardBuilder(id=" + this.id
                    + ", object=" + this.object
                    + ", created=" + this.created
                    + ", ownerBy=" + this.ownerBy
                    + ", root=" + this.root
                    + ", parent=" + this.parent
                    + ", permission=" + this.permission
                    + ")";
        }
    }
}
