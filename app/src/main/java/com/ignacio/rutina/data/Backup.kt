package com.ignacio.rutina.data

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Lo que se encontró al revisar un archivo antes de importarlo. */
sealed interface BackupCheck {
    data class Valid(val tasks: Int, val entries: Int) : BackupCheck

    data class Invalid(val reason: String) : BackupCheck
}

/**
 * Copias de seguridad: la base entera en un archivo que elige el usuario (Descargas, Drive…).
 * Sirven para cambiar de teléfono o pasar los datos de una instalación de la app a otra.
 */
class Backup(private val context: Context, private val database: RutinaDatabase) {
    /** Escribe la base en [uri]. Devuelve si se pudo. */
    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val file = context.getDatabasePath(RutinaDatabase.NAME)
        try {
            // Durante la transacción no escribe nadie más, así que el archivo no cambia mientras se copia.
            database.withTransaction {
                val output = context.contentResolver.openOutputStream(uri) ?: return@withTransaction false
                output.use { out -> file.inputStream().use { it.copyTo(out) } }
                true
            }
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Copia [uri] aparte y la revisa: tiene que ser una base de Rutina de esta versión o de una
     * anterior (las anteriores se migran acá). Si sirve, queda lista para [restore].
     */
    suspend fun check(uri: Uri): BackupCheck = withContext(Dispatchers.IO) {
        discard()
        val file = context.getDatabasePath(IMPORT_NAME).apply { parentFile?.mkdirs() }
        val copied = try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } != null
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
        val result = if (copied) inspect(file) else BackupCheck.Invalid("No se pudo leer el archivo.")
        if (result !is BackupCheck.Valid) discard()
        result
    }

    private fun inspect(file: File): BackupCheck {
        val version = try {
            if (!file.isSqlite()) return NOT_RUTINA
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val isRoom = db.rawQuery("SELECT 1 FROM sqlite_master WHERE name = 'room_master_table'", null)
                    .use { it.moveToFirst() }
                if (isRoom) db.version else 0
            }
        } catch (e: SQLException) {
            0
        }
        return when {
            version < 1 -> NOT_RUTINA
            version > database.openHelper.readableDatabase.version -> BackupCheck.Invalid(
                "La copia es de una versión más nueva de Rutina. Actualizá la app y probá de nuevo.",
            )
            else -> try {
                // Al abrirla, Room la migra a la versión actual y revisa que las tablas sean las que espera.
                val copy = RutinaDatabase.build(context, IMPORT_NAME)
                try {
                    copy.openHelper.writableDatabase
                        .query("SELECT (SELECT COUNT(*) FROM tasks), (SELECT COUNT(*) FROM entries)")
                        .use { cursor ->
                            cursor.moveToFirst()
                            BackupCheck.Valid(tasks = cursor.getInt(0), entries = cursor.getInt(1))
                        }
                } finally {
                    copy.close()
                }
            } catch (e: Exception) {
                NOT_RUTINA
            }
        }
    }

    /** Reemplaza todos los datos por los de la copia que dejó lista [check]. Devuelve si se pudo. */
    suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        try {
            // ATTACH no se puede hacer dentro de una transacción.
            db.execSQL("ATTACH DATABASE ? AS copia", arrayOf(context.getDatabasePath(IMPORT_NAME).path))
            try {
                // Si algo falla, la transacción se deshace y los datos quedan como estaban.
                database.withTransaction {
                    // Las claves foráneas se revisan al final, así que no importa en qué orden se cargan las tablas.
                    db.execSQL("PRAGMA defer_foreign_keys = ON")
                    val tables = db.names(
                        "SELECT name FROM main.sqlite_master WHERE type = 'table' " +
                            "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')",
                    )
                    tables.forEach { db.execSQL("DELETE FROM main.`$it`") }
                    tables.forEach { table ->
                        // Por nombre y no con *: una base migrada tiene las columnas nuevas al final, y una
                        // instalada de cero, en el orden de la entidad.
                        val columns = db.names("PRAGMA main.table_info(`$table`)").joinToString { "`$it`" }
                        db.execSQL("INSERT INTO main.`$table` ($columns) SELECT $columns FROM copia.`$table`")
                    }
                }
                true
            } finally {
                // Si no se puede, la copia queda adjunta hasta que se cierre la base, sin molestar.
                runCatching { db.execSQL("DETACH DATABASE copia") }
            }
        } catch (e: SQLException) {
            false
        } finally {
            discard()
        }
    }

    /** Borra la copia que se revisó y no se importó. */
    suspend fun discard() {
        withContext(Dispatchers.IO) { context.deleteDatabase(IMPORT_NAME) }
    }

    private fun File.isSqlite(): Boolean {
        val header = ByteArray(SQLITE_HEADER.size)
        return inputStream().use { it.read(header) } == header.size && header.contentEquals(SQLITE_HEADER)
    }

    /** La columna `name` de cada fila que devuelve [sql]. */
    private fun SupportSQLiteDatabase.names(sql: String): List<String> = query(sql).use { cursor ->
        val index = cursor.getColumnIndexOrThrow("name")
        buildList { while (cursor.moveToNext()) add(cursor.getString(index)) }
    }

    private companion object {
        /** Al lado de la base de la app, para que Room la pueda abrir y migrar con el mismo código. */
        const val IMPORT_NAME = "importar.db"

        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()

        val NOT_RUTINA = BackupCheck.Invalid("Ese archivo no es una copia de Rutina.")
    }
}
