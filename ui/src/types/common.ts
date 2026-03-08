/** Generic wrapper for Spring Boot paginated API responses. */
export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  numberOfElements: number
  empty: boolean
  sort?: SortInfo
}

export interface SortInfo {
  sorted: boolean
  ascending: boolean
  descending: boolean
  property: string
}
