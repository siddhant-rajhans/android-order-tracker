package com.siddhant.ordertracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siddhant.ordertracker.domain.model.Order
import com.siddhant.ordertracker.domain.model.OrderStatus
import com.siddhant.ordertracker.ui.orders.StatusChip
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    viewModel: OrderDetailViewModel,
    onBack: () -> Unit,
) {
    val order by viewModel.order.collectAsState()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order #${order?.id?.take(8) ?: "..."}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        order?.let { o ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                StatusSection(o)
                Spacer(modifier = Modifier.height(16.dp))
                ItemsSection(o)
                Spacer(modifier = Modifier.height(16.dp))
                TimestampsSection(o)
                Spacer(modifier = Modifier.height(24.dp))
                ActionsSection(
                    order = o,
                    isUpdating = state.isUpdating,
                    onAdvance = { viewModel.advanceStatus() },
                    onCancel = { viewModel.cancelOrder() },
                )

                state.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                if (o.pendingSync) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This update is pending sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } ?: run {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun StatusSection(order: Order) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Status", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            StatusChip(order.status)
            order.estimatedDelivery?.let { eta ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Estimated: ${formatTimestamp(eta.toEpochMilli())}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ItemsSection(order: Order) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Items", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            order.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${item.quantity}x ${item.name}")
                    Text(formatCurrency(item.priceCents * item.quantity))
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total", fontWeight = FontWeight.Bold)
                Text(formatCurrency(order.totalCents), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TimestampsSection(order: Order) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Timeline", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Created: ${formatTimestamp(order.createdAt.toEpochMilli())}")
            Text("Updated: ${formatTimestamp(order.updatedAt.toEpochMilli())}")
        }
    }
}

@Composable
private fun ActionsSection(
    order: Order,
    isUpdating: Boolean,
    onAdvance: () -> Unit,
    onCancel: () -> Unit,
) {
    val nextStatus = order.status.next()
    val canCancel = order.status != OrderStatus.DELIVERED && order.status != OrderStatus.CANCELLED

    Row(modifier = Modifier.fillMaxWidth()) {
        if (nextStatus != null) {
            Button(
                onClick = onAdvance,
                enabled = !isUpdating,
                modifier = Modifier.weight(1f),
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp).width(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Advance to ${nextStatus.name.replace('_', ' ')}")
                }
            }
        }

        if (canCancel && nextStatus != null) {
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (canCancel) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isUpdating,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Cancel")
            }
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    .withZone(ZoneId.systemDefault())

private fun formatTimestamp(epochMilli: Long): String =
    timeFormatter.format(java.time.Instant.ofEpochMilli(epochMilli))

private fun formatCurrency(cents: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    format.currency = Currency.getInstance("USD")
    return format.format(cents / 100.0)
}
