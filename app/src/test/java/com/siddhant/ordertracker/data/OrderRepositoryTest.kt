package com.siddhant.ordertracker.data

import com.siddhant.ordertracker.data.local.OrderDao
import com.siddhant.ordertracker.data.local.OrderEntity
import com.siddhant.ordertracker.data.remote.OrderApi
import com.siddhant.ordertracker.data.remote.OrderDto
import com.siddhant.ordertracker.data.remote.OrderItemDto
import com.siddhant.ordertracker.data.remote.StatusUpdateRequest
import com.siddhant.ordertracker.data.repository.OrderRepository
import com.siddhant.ordertracker.domain.model.OrderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrderRepositoryTest {

    private lateinit var fakeDao: FakeOrderDao
    private lateinit var fakeApi: FakeOrderApi
    private lateinit var repository: OrderRepository

    @Before
    fun setup() {
        fakeDao = FakeOrderDao()
        fakeApi = FakeOrderApi()
        repository = OrderRepository(fakeDao, fakeApi)
    }

    @Test
    fun `refresh fetches from API and stores locally`() = runBlocking {
        fakeApi.orders = listOf(
            makeDto("order-1", "Alice", "PLACED"),
            makeDto("order-2", "Bob", "CONFIRMED"),
        )

        val result = repository.refresh()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        assertEquals(2, fakeDao.entities.size)
    }

    @Test
    fun `refresh failure returns error but preserves local data`() = runBlocking {
        fakeDao.entities.add(makeEntity("cached-1", "PLACED"))
        fakeApi.shouldFail = true

        val result = repository.refresh()
        assertTrue(result.isFailure)
        assertEquals(1, fakeDao.entities.size)
    }

    @Test
    fun `updateStatus writes locally first then syncs`() = runBlocking {
        fakeDao.entities.add(makeEntity("order-1", "PLACED"))
        fakeApi.orders = listOf(makeDto("order-1", "Test", "CONFIRMED"))

        val result = repository.updateStatus("order-1", OrderStatus.CONFIRMED)
        assertTrue(result.isSuccess)
        assertEquals("CONFIRMED", fakeDao.entities.find { it.id == "order-1" }?.status)
    }

    @Test
    fun `updateStatus marks pending when API fails`() = runBlocking {
        fakeDao.entities.add(makeEntity("order-1", "PLACED"))
        fakeApi.shouldFail = true

        val result = repository.updateStatus("order-1", OrderStatus.CONFIRMED)
        assertTrue(result.isSuccess)

        val entity = fakeDao.entities.find { it.id == "order-1" }!!
        assertEquals("CONFIRMED", entity.status)
    }

    @Test
    fun `syncPending syncs offline changes`() = runBlocking {
        fakeDao.entities.add(makeEntity("order-1", "CONFIRMED", pendingSync = true))
        fakeDao.entities.add(makeEntity("order-2", "PREPARING", pendingSync = true))
        fakeApi.orders = listOf(
            makeDto("order-1", "Test", "CONFIRMED"),
            makeDto("order-2", "Test", "PREPARING"),
        )

        val result = repository.syncPending()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
    }

    @Test
    fun `syncPending partial failure syncs what it can`() = runBlocking {
        fakeDao.entities.add(makeEntity("order-1", "CONFIRMED", pendingSync = true))
        fakeDao.entities.add(makeEntity("order-2", "PREPARING", pendingSync = true))
        fakeApi.failForIds = setOf("order-2")
        fakeApi.orders = listOf(makeDto("order-1", "Test", "CONFIRMED"))

        val result = repository.syncPending()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
    }

    @Test
    fun `observeOrders returns flow of domain models`() = runBlocking {
        fakeDao.entities.add(makeEntity("order-1", "PLACED"))
        val orders = repository.observeOrders().first()
        assertEquals(1, orders.size)
        assertEquals("order-1", orders[0].id)
        assertEquals(OrderStatus.PLACED, orders[0].status)
    }

    @Test
    fun `pendingSyncCount reflects unsynced changes`() = runBlocking {
        fakeDao.entities.add(makeEntity("order-1", "CONFIRMED", pendingSync = true))
        fakeDao.entities.add(makeEntity("order-2", "PLACED", pendingSync = false))
        assertEquals(1, repository.pendingSyncCount())
    }

    private fun makeDto(id: String, name: String, status: String) = OrderDto(
        id = id,
        customerName = name,
        items = listOf(OrderItemDto("Item", 1, 1000)),
        status = status,
        totalCents = 1000,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        estimatedDelivery = null,
    )

    private fun makeEntity(id: String, status: String, pendingSync: Boolean = false) = OrderEntity(
        id = id,
        customerName = "Test",
        itemsJson = """[{"name":"Item","quantity":1,"priceCents":1000}]""",
        status = status,
        totalCents = 1000,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        estimatedDelivery = null,
        pendingSync = pendingSync,
    )
}

class FakeOrderDao : OrderDao {
    val entities = mutableListOf<OrderEntity>()
    private val flow = MutableStateFlow<List<OrderEntity>>(emptyList())

    override fun observeAll(): Flow<List<OrderEntity>> {
        flow.value = entities.sortedByDescending { it.updatedAt }
        return flow
    }

    override suspend fun getById(id: String): OrderEntity? = entities.find { it.id == id }

    override fun observeById(id: String): Flow<OrderEntity?> = flowOf(entities.find { it.id == id })

    override suspend fun upsert(order: OrderEntity) {
        entities.removeAll { it.id == order.id }
        entities.add(order)
    }

    override suspend fun upsertAll(orders: List<OrderEntity>) {
        for (o in orders) {
            entities.removeAll { it.id == o.id }
            entities.add(o)
        }
    }

    override suspend fun updateStatus(id: String, status: String, updatedAt: Long) {
        val idx = entities.indexOfFirst { it.id == id }
        if (idx >= 0) {
            entities[idx] = entities[idx].copy(status = status, updatedAt = updatedAt, pendingSync = true)
        }
    }

    override suspend fun getPendingSync(): List<OrderEntity> = entities.filter { it.pendingSync }

    override suspend fun markSynced(id: String) {
        val idx = entities.indexOfFirst { it.id == id }
        if (idx >= 0) {
            entities[idx] = entities[idx].copy(pendingSync = false)
        }
    }

    override suspend fun count(): Int = entities.size

    override suspend fun pendingSyncCount(): Int = entities.count { it.pendingSync }

    override suspend fun deleteAll() { entities.clear() }
}

class FakeOrderApi : OrderApi {
    var orders = emptyList<OrderDto>()
    var shouldFail = false
    var failForIds = emptySet<String>()

    override suspend fun getOrders(): List<OrderDto> {
        if (shouldFail) throw RuntimeException("API error")
        return orders
    }

    override suspend fun getOrder(id: String): OrderDto {
        if (shouldFail || id in failForIds) throw RuntimeException("API error")
        return orders.first { it.id == id }
    }

    override suspend fun updateStatus(id: String, request: StatusUpdateRequest): OrderDto {
        if (shouldFail || id in failForIds) throw RuntimeException("API error")
        val existing = orders.firstOrNull { it.id == id }
            ?: throw RuntimeException("Not found")
        return existing.copy(status = request.status)
    }
}
