package dev.langchain4j.model.mistralai;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralChatMessage {

    private MistralRoleName role;
    private String content;
}
