package com.siddhant.ordertracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE id = :id")
    fun observeById(id: String): Flow<OrderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(orders: List<OrderEntity>)

    @Query("UPDATE orders SET status = :status, updatedAt = :updatedAt, pendingSync = 1 WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("SELECT * FROM orders WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<OrderEntity>

    @Query("UPDATE orders SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM orders WHERE pendingSync = 1")
    suspend fun pendingSyncCount(): Int

    @Query("DELETE FROM orders")
    suspend fun deleteAll()
}
