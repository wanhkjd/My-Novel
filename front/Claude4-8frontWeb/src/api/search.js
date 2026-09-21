import request from '@/utils/request'
export const searchBooks = (condition) =>
  request.get('/front/search/books', { params: condition })
