package com.siddhant.ordertracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.siddhant.ordertracker.domain.model.Order
import com.siddhant.ordertracker.domain.model.OrderItem
import com.siddhant.ordertracker.domain.model.OrderStatus
import java.time.Instant

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val customerName: String,
    val itemsJson: String,
    val status: String,
    val totalCents: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val estimatedDelivery: Long?,
    val pendingSync: Boolean = false,
)

fun OrderEntity.toDomain(parseItems: (String) -> List<OrderItem>): Order = Order(
    id = id,
    customerName = customerName,
    items = parseItems(itemsJson),
    status = OrderStatus.valueOf(status),
    totalCents = totalCents,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    estimatedDelivery = estimatedDelivery?.let { Instant.ofEpochMilli(it) },
    pendingSync = pendingSync,
)

fun Order.toEntity(serializeItems: (List<OrderItem>) -> String): OrderEntity = OrderEntity(
    id = id,
    customerName = customerName,
    itemsJson = serializeItems(items),
    status = status.name,
    totalCents = totalCents,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    estimatedDelivery = estimatedDelivery?.toEpochMilli(),
    pendingSync = pendingSync,
)
