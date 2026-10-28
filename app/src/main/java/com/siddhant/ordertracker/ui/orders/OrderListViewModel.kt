package com.siddhant.ordertracker.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siddhant.ordertracker.data.repository.OrderRepository
import com.siddhant.ordertracker.domain.model.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OrderListState(
    val orders: List<Order> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val pendingSyncCount: Int = 0,
)

class OrderListViewModel(
    private val repository: OrderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OrderListState())
    val state: StateFlow<OrderListState> = _state.asStateFlow()

    val orders: StateFlow<List<Order>> = repository.observeOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
        loadPendingCount()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            repository.refresh().fold(
                onSuccess = {
                    _state.value = _state.value.copy(isRefreshing = false)
                    loadPendingCount()
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        error = e.message ?: "Failed to refresh",
                    )
                },
            )
        }
    }

    fun syncPending() {
        viewModelScope.launch {
            repository.syncPending()
            loadPendingCount()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun loadPendingCount() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                pendingSyncCount = repository.pendingSyncCount()
            )
        }
    }
}
