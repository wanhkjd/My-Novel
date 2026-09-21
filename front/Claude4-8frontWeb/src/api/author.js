import request from '@/utils/request'
export const getAuthorStatus = () => request.get('/author/status')
export const authorRegister = (dto) => request.post('/author/register', dto)
export const getAuthorInfo = () => request.get('/author/info')
export const listAuthorBooks = (params) => request.get('/author/books', { params })
export const publishBook = (dto) => request.post('/author/book', dto)
export const listAuthorChapters = (bookId, params) =>
  request.get(`/author/book/chapters/${bookId}`, { params })
export const publishChapter = (bookId, dto) => request.post(`/author/book/chapter/${bookId}`, dto)
export const getAuthorChapter = (chapterId) => request.get(`/author/book/chapter/${chapterId}`)
export const updateAuthorChapter = (chapterId, dto) =>
  request.put(`/author/book/chapter/${chapterId}`, dto)
export const deleteAuthorChapter = (chapterId) => request.delete(`/author/book/chapter/${chapterId}`)
