package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public final class CharacterSetting {

	private String name;
	private String gender;
	private String identity;
	private String nickname;
	@SerializedName("feeling_toward")
	private List<CharacterFeelingToward> feelingTowards;

	@SerializedName("detail_setting")
	private String detailSetting;

	@SerializedName("other_setting")
	private Map<String, Object> otherSetting;

}