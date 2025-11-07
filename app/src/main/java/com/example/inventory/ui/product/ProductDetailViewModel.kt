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

package com.example.inventory.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.Item
import com.example.inventory.data.ItemsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for ProductDetailScreen
 */
class ProductDetailViewModel(
    savedStateHandle: SavedStateHandle,
    itemsRepository: ItemsRepository
) : ViewModel() {

    private val itemId: Int = checkNotNull(savedStateHandle[ProductDetailDestination.itemIdArg])

    private val _quantityInput = MutableStateFlow(savedStateHandle.get<String>("quantityInput") ?: "")
    
    // Save quantity input to saved state handle when it changes
    init {
        _quantityInput.value = savedStateHandle.get<String>("quantityInput") ?: ""
    }

    val uiState: StateFlow<ProductDetailUiState> =
        combine(
            itemsRepository.getItemStream(itemId),
            _quantityInput
        ) { item, quantityInput ->
            val quantityInt = quantityInput.toIntOrNull()
            val hasError = when {
                quantityInput.isBlank() -> false
                quantityInt == null -> true
                quantityInt <= 0 -> true
                item != null && quantityInt > item.quantity -> true
                else -> false
            }
            
            val errorMessage = when {
                quantityInput.isBlank() -> null
                quantityInt == null -> "Invalid quantity"
                quantityInt <= 0 -> "Invalid quantity"
                item != null && quantityInt > item.quantity -> "Insufficient stock. Available: ${item.quantity}"
                else -> null
            }
            
            ProductDetailUiState(
                item = item,
                quantityInput = quantityInput,
                hasError = hasError,
                errorMessage = errorMessage
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ProductDetailUiState()
            )

    private val _savedStateHandle = savedStateHandle

    /**
     * Update the quantity input and validate it
     */
    fun updateQuantity(quantity: String) {
        _savedStateHandle["quantityInput"] = quantity
        _quantityInput.value = quantity
    }

    /**
     * Submit the order and navigate to confirmation screen
     */
    fun submitOrder(onNavigate: (Int, Int) -> Unit) {
        viewModelScope.launch {
            val currentState = uiState.value
            val item = currentState.item
            val quantity = currentState.quantityInput.toIntOrNull()
            
            if (item != null && quantity != null && quantity > 0 && quantity <= item.quantity) {
                onNavigate(item.id, quantity)
            }
        }
    }
}

/**
 * Ui State for ProductDetailScreen
 */
data class ProductDetailUiState(
    val item: Item? = null,
    val quantityInput: String = "",
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

