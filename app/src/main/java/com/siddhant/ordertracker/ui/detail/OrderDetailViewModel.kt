package com.siddhant.ordertracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siddhant.ordertracker.data.repository.OrderRepository
import com.siddhant.ordertracker.domain.model.Order
import com.siddhant.ordertracker.domain.model.OrderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailState(
    val isUpdating: Boolean = false,
    val error: String? = null,
)

class OrderDetailViewModel(
    private val orderId: String,
    private val repository: OrderRepository,
) : ViewModel() {

    val order: StateFlow<Order?> = repository.observeOrder(orderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun advanceStatus() {
        val current = order.value ?: return
        val next = current.status.next() ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isUpdating = true, error = null)
            repository.updateStatus(orderId, next).fold(
                onSuccess = {
                    _state.value = _state.value.copy(isUpdating = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isUpdating = false,
                        error = e.message ?: "Update failed",
                    )
                },
            )
        }
    }

    fun cancelOrder() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isUpdating = true, error = null)
            repository.updateStatus(orderId, OrderStatus.CANCELLED).fold(
                onSuccess = {
                    _state.value = _state.value.copy(isUpdating = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isUpdating = false,
                        error = e.message ?: "Cancel failed",
                    )
                },
            )
        }
    }
}
