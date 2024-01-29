package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MistralAiModelCard {

    private String id;
    private String object;
    private Integer created;
    private String ownerBy;
    private String root;
    private String parent;
    private List<MistralAiModelPermission> permission;
}
