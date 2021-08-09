package one.mixin.android.vo

import androidx.room.ColumnInfo

data class ForwardUser(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "app_id")
    val appId: String?,
    @ColumnInfo(name = "encrypted")
    val encrypted: Boolean?,
)

fun ForwardUser.encryptedCategory(): EncryptCategory = if (appId != null && encrypted == true) {
    EncryptCategory.ENCRYPTED
} else if (appId != null) {
    EncryptCategory.PLAIN
} else {
    EncryptCategory.SIGNAL
}
