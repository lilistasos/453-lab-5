# Code Changes for Lab 5 - Ordering Process

This document contains all the code that was added to Code Lab 15 to implement the ordering process functionality.

## Summary of Changes

The following features were added:
1. Search functionality on the home screen
2. Product detail screen with quantity input
3. Order confirmation screen
4. Navigation between screens
5. Data preservation using ViewModel and SavedStateHandle

---

## 1. Database Layer Changes

### ItemDao.kt - Added Search Methods

```kotlin
@Query("SELECT * from items WHERE id = :id")
suspend fun getItemById(id: Int): Item?

@Query("SELECT * from items WHERE name LIKE :name LIMIT 1")
suspend fun getItemByName(name: String): Item?
```

### ItemsRepository.kt - Added Search Interface Methods

```kotlin
/**
 * Search for an item by ID
 */
suspend fun searchItemById(id: Int): Item?

/**
 * Search for an item by name
 */
suspend fun searchItemByName(name: String): Item?
```

### OfflineItemsRepository.kt - Implemented Search Methods

```kotlin
override suspend fun searchItemById(id: Int): Item? = itemDao.getItemById(id)

override suspend fun searchItemByName(name: String): Item? {
    val searchPattern = "%$name%"
    return itemDao.getItemByName(searchPattern)
}
```

---

## 2. Home Screen Changes

### HomeScreen.kt - Added Search Functionality

**Key additions:**
- Search TextField with search icon
- Search functionality that navigates to ProductDetailScreen
- Error dialog for product not found

**Main changes:**
```kotlin
@Composable
fun HomeScreen(
    navigateToItemEntry: () -> Unit,
    navigateToItemUpdate: (Int) -> Unit,
    navigateToProductDetail: (Int) -> Unit,  // NEW
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    // ... existing code ...
    HomeBody(
        itemList = homeUiState.itemList,
        onItemClick = navigateToItemUpdate,
        onSearch = { query, onResult ->
            viewModel.searchItem(query) { item ->
                if (item != null) {
                    navigateToProductDetail(item.id)
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
        },
        // ...
    )
}
```

### HomeViewModel.kt - Added Search Method

```kotlin
/**
 * Search for an item by ID or name
 */
fun searchItem(query: String, onResult: (Item?) -> Unit) {
    viewModelScope.launch {
        val item = if (query.isBlank()) {
            null
        } else {
            // Try to parse as ID first
            val id = query.toIntOrNull()
            if (id != null) {
                itemsRepository.searchItemById(id)
            } else {
                itemsRepository.searchItemByName(query)
            }
        }
        onResult(item)
    }
}
```

---

## 3. Product Detail Screen (NEW FILE)

### ProductDetailScreen.kt

**Complete file created** - Shows product information and allows quantity input:

```kotlin
object ProductDetailDestination : NavigationDestination {
    override val route = "product_detail"
    override val titleRes = R.string.product_detail_title
    const val itemIdArg = "itemId"
    val routeWithArgs = "$route/{$itemIdArg}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    navigateToOrderConfirmation: (Int, Int) -> Unit,
    navigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProductDetailViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            InventoryTopAppBar(
                title = stringResource(ProductDetailDestination.titleRes),
                canNavigateBack = true,
                navigateUp = navigateBack
            )
        },
        // ... displays product info and quantity input field
    )
}
```

### ProductDetailViewModel.kt

**Complete file created** - Manages product detail state with data preservation:

```kotlin
class ProductDetailViewModel(
    savedStateHandle: SavedStateHandle,
    itemsRepository: ItemsRepository
) : ViewModel() {

    private val itemId: Int = checkNotNull(savedStateHandle[ProductDetailDestination.itemIdArg])
    private val _quantityInput = MutableStateFlow(savedStateHandle.get<String>("quantityInput") ?: "")

    val uiState: StateFlow<ProductDetailUiState> =
        combine(
            itemsRepository.getItemStream(itemId),
            _quantityInput
        ) { item, quantityInput ->
            // Validation logic
            ProductDetailUiState(
                item = item,
                quantityInput = quantityInput,
                hasError = hasError,
                errorMessage = errorMessage
            )
        }
        .stateIn(...)

    fun updateQuantity(quantity: String) {
        _savedStateHandle["quantityInput"] = quantity  // Preserves data
        _quantityInput.value = quantity
    }

    fun submitOrder(onNavigate: (Int, Int) -> Unit) {
        // Navigates to order confirmation
    }
}
```

---

## 4. Order Confirmation Screen (NEW FILE)

### OrderConfirmationScreen.kt

**Complete file created** - Displays order details:

```kotlin
object OrderConfirmationDestination : NavigationDestination {
    override val route = "order_confirmation"
    override val titleRes = R.string.order_confirmation_title
    const val itemIdArg = "itemId"
    const val quantityArg = "quantity"
    val routeWithArgs = "$route/{$itemIdArg}/{$quantityArg}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderConfirmationScreen(
    navigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrderConfirmationViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            InventoryTopAppBar(
                title = stringResource(OrderConfirmationDestination.titleRes),
                canNavigateBack = true,
                navigateUp = navigateBack
            )
        },
        // ... displays order details
    )
}
```

### OrderConfirmationViewModel.kt

**Complete file created** - Manages order confirmation and updates inventory:

```kotlin
class OrderConfirmationViewModel(
    savedStateHandle: SavedStateHandle,
    itemsRepository: ItemsRepository
) : ViewModel() {

    private val itemId: Int = checkNotNull(savedStateHandle[OrderConfirmationDestination.itemIdArg])
    private val quantityOrdered: Int = checkNotNull(savedStateHandle[OrderConfirmationDestination.quantityArg])

    init {
        // Update inventory when order is confirmed
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
                    OrderConfirmationUiState(
                        item = item,
                        quantityOrdered = quantityOrdered,
                        totalCost = item.price * quantityOrdered,
                        remainingItems = item.quantity - quantityOrdered
                    )
                } else {
                    OrderConfirmationUiState()
                }
            }
            .stateIn(...)
}
```

---

## 5. Navigation Changes

### InventoryNavGraph.kt - Added New Routes

```kotlin
composable(
    route = ProductDetailDestination.routeWithArgs,
    arguments = listOf(navArgument(ProductDetailDestination.itemIdArg) {
        type = NavType.IntType
    })
) {
    ProductDetailScreen(
        navigateToOrderConfirmation = { itemId, quantity ->
            navController.navigate("${OrderConfirmationDestination.route}/${itemId}/${quantity}")
        },
        navigateBack = { navController.navigateUp() }
    )
}
composable(
    route = OrderConfirmationDestination.routeWithArgs,
    arguments = listOf(
        navArgument(OrderConfirmationDestination.itemIdArg) {
            type = NavType.IntType
        },
        navArgument(OrderConfirmationDestination.quantityArg) {
            type = NavType.IntType
        }
    )
) {
    OrderConfirmationScreen(
        navigateBack = { navController.navigateUp() }
    )
}
```

### Updated HomeScreen Navigation

```kotlin
composable(route = HomeDestination.route) {
    HomeScreen(
        navigateToItemEntry = { navController.navigate(ItemEntryDestination.route) },
        navigateToItemUpdate = {
            navController.navigate("${ItemDetailsDestination.route}/${it}")
        },
        navigateToProductDetail = {  // NEW
            navController.navigate("${ProductDetailDestination.route}/${it}")
        }
    )
}
```

---

## 6. ViewModel Provider Updates

### AppViewModelProvider.kt - Added New ViewModels

```kotlin
// Initializer for ProductDetailViewModel
initializer {
    ProductDetailViewModel(
        this.createSavedStateHandle(),
        inventoryApplication().container.itemsRepository
    )
}

// Initializer for OrderConfirmationViewModel
initializer {
    OrderConfirmationViewModel(
        this.createSavedStateHandle(),
        inventoryApplication().container.itemsRepository
    )
}
```

---

## 7. String Resources

### strings.xml - Added New Strings

```xml
<string name="search">Search</string>
<string name="search_hint">Search by ID or name</string>
<string name="product_detail_title">Product Details</string>
<string name="quantity_to_order">Quantity to Order</string>
<string name="order">Order</string>
<string name="order_confirmation_title">Order Confirmation</string>
<string name="quantity_ordered">Quantity Ordered</string>
<string name="total_cost">Total Cost</string>
<string name="remaining_items">Remaining Items</string>
<string name="product_not_found">Product not found</string>
<string name="invalid_quantity">Invalid quantity</string>
<string name="insufficient_stock">Insufficient stock. Available: %d</string>
```

---

## Key Features Implemented

1. **Search Functionality**: Users can search by product ID or name from the home screen
2. **Product Detail Screen**: Displays product information and allows quantity input with validation
3. **Order Confirmation**: Shows complete order details and updates inventory
4. **Navigation**: Proper navigation flow using NavHostController and routes
5. **Data Preservation**: Quantity input is preserved when navigating back using SavedStateHandle
6. **App Bar**: Up button properly navigates back to previous screen

---

## Files Created

1. `app/src/main/java/com/example/inventory/ui/product/ProductDetailScreen.kt`
2. `app/src/main/java/com/example/inventory/ui/product/ProductDetailViewModel.kt`
3. `app/src/main/java/com/example/inventory/ui/order/OrderConfirmationScreen.kt`
4. `app/src/main/java/com/example/inventory/ui/order/OrderConfirmationViewModel.kt`

## Files Modified

1. `app/src/main/java/com/example/inventory/data/ItemDao.kt`
2. `app/src/main/java/com/example/inventory/data/ItemsRepository.kt`
3. `app/src/main/java/com/example/inventory/data/OfflineItemsRepository.kt`
4. `app/src/main/java/com/example/inventory/ui/home/HomeScreen.kt`
5. `app/src/main/java/com/example/inventory/ui/home/HomeViewModel.kt`
6. `app/src/main/java/com/example/inventory/ui/navigation/InventoryNavGraph.kt`
7. `app/src/main/java/com/example/inventory/ui/AppViewModelProvider.kt`
8. `app/src/main/res/values/strings.xml`

