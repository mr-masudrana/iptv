package com.iptv.player.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val url: String,
    val name: String,
    val logo: String?,
    val groupName: String
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,   // http(s) URL or content:// URI
    val isFile: Boolean,
    val position: Int = 0
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY name")
    fun all(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun delete(url: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY position, id")
    fun all(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY position, id")
    suspend fun allOnce(): List<PlaylistEntity>

    @Insert
    suspend fun insert(p: PlaylistEntity): Long

    @Query("SELECT MAX(position) FROM playlists")
    suspend fun maxPosition(): Int?

    @Update
    suspend fun updateAll(items: List<PlaylistEntity>)

    @Update
    suspend fun update(p: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `source` TEXT NOT NULL, `isFile` INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `playlists` SET `position` = `id`")
    }
}

@Database(entities = [FavoriteEntity::class, PlaylistEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "iptv.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
