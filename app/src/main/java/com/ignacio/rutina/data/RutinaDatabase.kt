package com.ignacio.rutina.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Task::class, Entry::class, Category::class],
    version = 4,
    autoMigrations = [
        // 1 → 2: íconos de las tareas.
        AutoMigration(from = 1, to = 2),
        // 2 → 3: categorías, a mano en ADD_CATEGORIES.
        // 3 → 4: objetivo de los contadores, a mano en ADD_COUNTER_GOAL.
    ],
)
@TypeConverters(Converters::class)
abstract class RutinaDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun entryDao(): EntryDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val NAME = "rutina.db"

        /** La base de la app, o con otro [name] una copia que se está por importar (ver [Backup]). */
        fun build(context: Context, name: String = NAME): RutinaDatabase =
            Room.databaseBuilder(context, RutinaDatabase::class.java, name)
                // En WAL, Android no fuerza la escritura a disco en cada commit (sync NORMAL) y un apagón
                // puede borrar lo último que se marcó. Sin WAL usa sync FULL: cada registro queda guardado.
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(ADD_CATEGORIES, ADD_COUNTER_GOAL)
                .addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) = insertDefaultCategories(db)
                    },
                )
                .build()

        /**
         * La migración automática recreaba la tabla de tareas (por el índice) y borrarla puede
         * arrastrar en cascada los registros. Así solo se agrega la columna.
         */
        private val ADD_CATEGORIES = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `categoryId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)")
                insertDefaultCategories(db)
            }
        }

        /** Igual que en ADD_CATEGORIES: solo se agrega la columna, sin recrear la tabla de tareas. */
        private val ADD_COUNTER_GOAL = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `goal` TEXT NOT NULL DEFAULT 'MORE'")
            }
        }

        private fun insertDefaultCategories(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO categories (name) VALUES ('Trabajo'), ('Vida cotidiana'), ('Ejercicio')")
        }
    }
}
