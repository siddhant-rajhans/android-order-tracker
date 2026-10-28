package com.siddhant.ordertracker.domain.model

import java.time.Instant

enum class OrderStatus {
    PLACED,
    CONFIRMED,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    fun next(): OrderStatus? = when (this) {
        PLACED -> CONFIRMED
        CONFIRMED -> PREPARING
        PREPARING -> OUT_FOR_DELIVERY
        OUT_FOR_DELIVERY -> DELIVERED
        DELIVERED -> null
        CANCELLED -> null
    }
}

data class Order(
    val id: String,
    val customerName: String,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val totalCents: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val estimatedDelivery: Instant? = null,
    val pendingSync: Boolean = false,
)

data class OrderItem(
    val name: String,
    val quantity: Int,
    val priceCents: Long,
)

data class StatusUpdate(
    val orderId: String,
    val oldStatus: OrderStatus,
    val newStatus: OrderStatus,
    val timestamp: Instant,
)
