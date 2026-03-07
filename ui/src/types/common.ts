export interface PagedResponse<T> {
  content: T[]
  empty: boolean
  totalPages: number
  totalElements: number
  first: boolean
  last: boolean
}
