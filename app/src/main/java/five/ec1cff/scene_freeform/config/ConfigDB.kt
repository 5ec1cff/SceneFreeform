package five.ec1cff.scene_freeform.config

import androidx.room.*
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import kotlinx.coroutines.flow.Flow

@Database(entities = [Config::class, NotificationScope::class, AppJumpRule::class], version = 1)
abstract class ConfigDB : RoomDatabase() {

    abstract fun configsDao(): ModuleConfigsDao

    @Dao
    interface ModuleConfigsDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun setConfig(vararg config: Config)

        @Query("SELECT * FROM Config WHERE name = :name")
        fun getConfig(name: String): Config

        @Query("SELECT * FROM Config")
        fun getAllConfigs(): List<Config>

        @Query("SELECT * FROM Config")
        fun getAllConfigsFlow(): Flow<List<Config>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun setNotificationScopes(vararg scope: NotificationScope)

        @Query("SELECT * FROM NotificationScope")
        suspend fun getNotificationScopes(): List<NotificationScope>

        @Query("SELECT COUNT(*) FROM NotificationScope")
        fun getNotificationScopesCountFlow(): Flow<Int>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun setAppJumpRules(vararg rule: AppJumpRule)

        @Query("SELECT * FROM AppJumpRule")
        suspend fun getAppJumpRules(): List<AppJumpRule>

        @Query("SELECT COUNT(*) FROM AppJumpRule")
        fun getAppJumpRulesCountFlow(): Flow<Int>
    }
}