package com.ignacio.rutina.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}

@Dao
abstract class CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    abstract fun observeAll(): Flow<List<Category>>

    @Insert
    abstract suspend fun insert(category: Category): Long

    @Update
    abstract suspend fun update(category: Category)

    @Query("UPDATE tasks SET categoryId = NULL WHERE categoryId = :categoryId")
    abstract suspend fun detachTasks(categoryId: Long)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    abstract suspend fun deleteById(categoryId: Long)

    /** Las tareas de la categoría no se borran: quedan sin categoría. */
    @Transaction
    open suspend fun delete(category: Category) {
        detachTasks(category.id)
        deleteById(category.id)
    }
}

@Dao
abstract class EntryDao {
    @Query("SELECT * FROM entries WHERE slotDate = :date")
    abstract fun observeSlotsOn(date: LocalDate): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE slotDate IS NULL AND doneAt >= :from AND doneAt < :to ORDER BY doneAt")
    abstract fun observeCountsBetween(from: Long, to: Long): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE taskId = :taskId ORDER BY doneAt")
    abstract fun observeForTask(taskId: Long): Flow<List<Entry>>

    @Query("SELECT COUNT(*) > 0 FROM entries WHERE taskId = :taskId AND slotDate = :date AND slotTime = :time")
    abstract suspend fun isSlotDone(taskId: Long, date: LocalDate, time: LocalTime): Boolean

    @Query("DELETE FROM entries WHERE taskId = :taskId AND slotDate = :date AND slotTime = :time")
    abstract suspend fun deleteSlot(taskId: Long, date: LocalDate, time: LocalTime)

    @Insert
    abstract suspend fun insert(entry: Entry): Long

    @Delete
    abstract suspend fun delete(entry: Entry)

    /** Marca una ocurrencia como hecha, reemplazando la hora si ya estaba marcada. */
    @Transaction
    open suspend fun markSlot(entry: Entry) {
        deleteSlot(entry.taskId, entry.slotDate!!, entry.slotTime!!)
        insert(entry)
    }
}
