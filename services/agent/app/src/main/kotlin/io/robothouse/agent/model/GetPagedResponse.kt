package io.robothouse.agent.model

import org.springframework.data.domain.Page

/**
 * Generic paged response wrapper for paginated API endpoints.
 */
data class GetPagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
    val numberOfElements: Int,
    val empty: Boolean,
    val sort: SortInfo?
) {
    companion object {
        fun <T> from(page: Page<T>): GetPagedResponse<T> {
            val sortOrder = page.sort.toList().firstOrNull()
            return GetPagedResponse(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                first = page.isFirst,
                last = page.isLast,
                numberOfElements = page.numberOfElements,
                empty = page.isEmpty,
                sort = sortOrder?.let {
                    SortInfo(
                        sorted = true,
                        ascending = it.isAscending,
                        descending = it.isDescending,
                        property = it.property
                    )
                }
            )
        }
    }
}

data class SortInfo(
    val sorted: Boolean,
    val ascending: Boolean,
    val descending: Boolean,
    val property: String
)
