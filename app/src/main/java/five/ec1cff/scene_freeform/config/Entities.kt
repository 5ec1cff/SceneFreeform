package five.ec1cff.scene_freeform.config

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Config(
    @PrimaryKey val name: String,
    val type: String,
    val value: String
) {
    companion object {
        const val TYPE_BOOLEAN = "boolean"
    }
}

@Entity
data class NotificationScope(
    @PrimaryKey val packageName: String,
    val inWhitelist: Boolean,
    val inBlacklist: Boolean
)

@Entity
data class AppJumpRule(
    @PrimaryKey val packageName: String,
    val allowSource: Boolean,
    val allowTarget: Boolean
)
