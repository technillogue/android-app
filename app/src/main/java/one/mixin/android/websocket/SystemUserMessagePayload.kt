package one.mixin.android.websocket

import com.google.gson.annotations.SerializedName

class SystemUserMessagePayload(
    @SerializedName("action")
    val action: String,
    @SerializedName("user_id")
    val userId: String
)

enum class SystemUserMessageAction { UPDATE }
