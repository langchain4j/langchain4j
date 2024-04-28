package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CharacterRoleSetting {

	@SerializedName("user_name")
	private String userName;

	@SerializedName("primary_bot_name")
	private String primaryBotName;
}
