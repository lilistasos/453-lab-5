/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.inventory.ui.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.Item
import com.example.inventory.data.ItemsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * ViewModel for OrderConfirmationScreen
 */
class OrderConfirmationViewModel(
    savedStateHandle: SavedStateHandle,
    itemsRepository: ItemsRepository
) : ViewModel() {

    private val itemId: Int = checkNotNull(savedStateHandle[OrderConfirmationDestination.itemIdArg])
    private val quantityOrdered: Int = checkNotNull(savedStateHandle[OrderConfirmationDestination.quantityArg])

    init {
        // Update the item quantity in the database when ViewModel is created
        viewModelScope.launch {
            val item = itemsRepository.getItemStream(itemId).first()
            
            if (item != null && item.quantity >= quantityOrdered) {
                val remainingItems = item.quantity - quantityOrdered
                itemsRepository.updateItem(item.copy(quantity = remainingItems))
            }
        }
    }

    val uiState: StateFlow<OrderConfirmationUiState> =
        itemsRepository.getItemStream(itemId)
            .map { item ->
                if (item != null) {
                    val totalCost = item.price * quantityOrdered
                    // Calculate remaining items (accounting for the order that was just placed)
                    val remainingItems = item.quantity - quantityOrdered
                    
                    OrderConfirmationUiState(
                        item = item,
                        quantityOrdered = quantityOrdered,
                        totalCost = totalCost,
                        remainingItems = maxOf(0, remainingItems)
                    )
                } else {
                    OrderConfirmationUiState()
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = OrderConfirmationUiState()
            )
}

/**
 * Ui State for OrderConfirmationScreen
 */
data class OrderConfirmationUiState(
    val item: Item? = null,
    val quantityOrdered: Int = 0,
    val totalCost: Double = 0.0,
    val remainingItems: Int = 0
)

