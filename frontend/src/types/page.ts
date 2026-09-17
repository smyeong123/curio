/** Spring Data page envelope, as every paginated endpoint returns it. */
export interface SpringPage<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
}
