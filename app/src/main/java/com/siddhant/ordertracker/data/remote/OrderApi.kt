package com.siddhant.ordertracker.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

data class OrderDto(
    val id: String,
    val customerName: String,
    val items: List<OrderItemDto>,
    val status: String,
    val totalCents: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val estimatedDelivery: Long?,
)

data class OrderItemDto(
    val name: String,
    val quantity: Int,
    val priceCents: Long,
)

data class StatusUpdateRequest(
    val status: String,
)

interface OrderApi {
    @GET("orders")
    suspend fun getOrders(): List<OrderDto>

    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") id: String): OrderDto

    @PUT("orders/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: String,
        @Body request: StatusUpdateRequest,
    ): OrderDto
}
