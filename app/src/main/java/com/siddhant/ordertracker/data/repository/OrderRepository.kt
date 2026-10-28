package com.siddhant.ordertracker.data.repository

import com.siddhant.ordertracker.data.local.OrderDao
import com.siddhant.ordertracker.data.local.OrderEntity
import com.siddhant.ordertracker.data.local.toDomain
import com.siddhant.ordertracker.data.remote.OrderApi
import com.siddhant.ordertracker.data.remote.OrderDto
import com.siddhant.ordertracker.data.remote.StatusUpdateRequest
import com.siddhant.ordertracker.domain.model.Order
import com.siddhant.ordertracker.domain.model.OrderItem
import com.siddhant.ordertracker.domain.model.OrderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class OrderRepository(
    private val dao: OrderDao,
    private val api: OrderApi,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun observeOrders(): Flow<List<Order>> =
        dao.observeAll().map { entities ->
            entities.map { it.toDomain(::parseItems) }
        }

    fun observeOrder(id: String): Flow<Order?> =
        dao.observeById(id).map { it?.toDomain(::parseItems) }

    suspend fun refresh(): Result<Int> = runCatching {
        val dtos = api.getOrders()
        val entities = dtos.map { it.toEntity() }
        dao.upsertAll(entities)
        entities.size
    }

    suspend fun updateStatus(orderId: String, newStatus: OrderStatus): Result<Order> = runCatching {
        val now = Instant.now().toEpochMilli()
        dao.updateStatus(orderId, newStatus.name, now)

        try {
            val dto = api.updateStatus(orderId, StatusUpdateRequest(newStatus.name))
            val entity = dto.toEntity()
            dao.upsert(entity.copy(pendingSync = false))
            entity.toDomain(::parseItems)
        } catch (_: Exception) {
            dao.getById(orderId)!!.toDomain(::parseItems)
        }
    }

    suspend fun syncPending(): Result<Int> = runCatching {
        val pending = dao.getPendingSync()
        var synced = 0
        for (entity in pending) {
            try {
                api.updateStatus(entity.id, StatusUpdateRequest(entity.status))
                dao.markSynced(entity.id)
                synced++
            } catch (_: Exception) {
                // will retry on next sync
            }
        }
        synced
    }

    suspend fun pendingSyncCount(): Int = dao.pendingSyncCount()

    private fun parseItems(itemsJson: String): List<OrderItem> = try {
        @Suppress("JSON_FORMAT_REDUNDANT")
        json.decodeFromString<List<SerializableItem>>(itemsJson)
            .map { OrderItem(it.name, it.quantity, it.priceCents) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun serializeItems(items: List<OrderItem>): String =
        json.encodeToString(items.map { SerializableItem(it.name, it.quantity, it.priceCents) })

    private fun OrderDto.toEntity(): OrderEntity = OrderEntity(
        id = id,
        customerName = customerName,
        itemsJson = json.encodeToString(
            items.map { SerializableItem(it.name, it.quantity, it.priceCents) }
        ),
        status = status,
        totalCents = totalCents,
        createdAt = createdAt,
        updatedAt = updatedAt,
        estimatedDelivery = estimatedDelivery,
        pendingSync = false,
    )
}

@kotlinx.serialization.Serializable
private data class SerializableItem(
    val name: String,
    val quantity: Int,
    val priceCents: Long,
)
