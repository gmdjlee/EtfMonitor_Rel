package com.etfmonitor.core.ui.component.statistics

import kotlinx.coroutines.flow.StateFlow

/**
 * Sort order enumeration for table column sorting.
 * Supports three-state cycling: NONE -> DESCENDING -> ASCENDING -> NONE
 */
enum class SortOrder {
    NONE,       // Default (no sorting)
    ASCENDING,  // Ascending order
    DESCENDING; // Descending order

    /**
     * Cycles to the next sort order.
     * NONE -> DESCENDING -> ASCENDING -> NONE
     */
    fun next(): SortOrder = when (this) {
        NONE -> DESCENDING
        DESCENDING -> ASCENDING
        ASCENDING -> NONE
    }
}

/**
 * Columns available for sorting in amount ranking table.
 */
enum class SortColumn {
    STOCK_NAME,           // Stock name
    TOTAL_AMOUNT,         // Total amount
    ETF_COUNT,            // ETF count
    NEW_ETF_COUNT,        // New ETF count
    INCREASED_ETF_COUNT,  // Increased ETF count
    DECREASED_ETF_COUNT,  // Decreased ETF count
    REMOVED_ETF_COUNT     // Removed ETF count
}

/**
 * Represents a single sort criterion with column and order.
 */
data class SortCriterion(
    val column: SortColumn,
    val order: SortOrder
)

/**
 * Interface for controlling table sorting functionality.
 * Decouples UI components from specific ViewModel implementations,
 * enabling shared components to be used across different feature modules.
 */
interface SortController {
    /**
     * Current list of active sort criteria.
     * Multiple criteria can be active for multi-column sorting.
     */
    val sortCriteria: StateFlow<List<SortCriterion>>

    /**
     * Gets the current sort order for a specific column.
     * @param column The column to check
     * @return The current sort order (NONE if not sorted)
     */
    fun getSortOrder(column: SortColumn): SortOrder

    /**
     * Gets the sort priority (1-based) for a specific column in multi-column sorting.
     * @param column The column to check
     * @return Priority number (1 = first, 2 = second, etc.) or 0 if not in sort criteria
     */
    fun getSortPriority(column: SortColumn): Int

    /**
     * Toggles or applies sorting for the specified column.
     * Cycles through: NONE -> DESCENDING -> ASCENDING -> NONE
     * @param column The column to sort by
     */
    fun sortAmountRankingBy(column: SortColumn)

    /**
     * Clears all active sort criteria, resetting to default order.
     */
    fun clearAllSorting()
}
