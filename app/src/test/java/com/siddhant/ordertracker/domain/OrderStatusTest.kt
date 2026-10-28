package com.siddhant.ordertracker.domain

import com.siddhant.ordertracker.domain.model.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderStatusTest {

    @Test
    fun `status transitions follow correct order`() {
        assertEquals(OrderStatus.CONFIRMED, OrderStatus.PLACED.next())
        assertEquals(OrderStatus.PREPARING, OrderStatus.CONFIRMED.next())
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.PREPARING.next())
        assertEquals(OrderStatus.DELIVERED, OrderStatus.OUT_FOR_DELIVERY.next())
    }

    @Test
    fun `delivered has no next status`() {
        assertNull(OrderStatus.DELIVERED.next())
    }

    @Test
    fun `cancelled has no next status`() {
        assertNull(OrderStatus.CANCELLED.next())
    }

    @Test
    fun `all statuses have names matching enum`() {
        OrderStatus.entries.forEach { status ->
            assertEquals(status, OrderStatus.valueOf(status.name))
        }
    }

    @Test
    fun `full lifecycle has 5 states`() {
        var current: OrderStatus? = OrderStatus.PLACED
        val visited = mutableListOf<OrderStatus>()
        while (current != null) {
            visited.add(current)
            current = current.next()
        }
        assertEquals(5, visited.size)
        assertEquals(OrderStatus.DELIVERED, visited.last())
    }
}
