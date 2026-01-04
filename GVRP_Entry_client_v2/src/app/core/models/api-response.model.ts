export interface PaginatedResponse<T> {
  content: T[];
  page_no: number;
  page_size: number;
  total_elements: number;
  total_pages: number;
}

export interface ApiError {
  message: string;
  status: number;
  timestamp: string;
}
