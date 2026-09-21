import request, { putForm } from '@/utils/request'
export const register = (dto) => request.post('/front/user/register', dto)
export const login = (dto) => request.post('/front/user/login', dto)
export const getUserInfo = () => request.get('/front/user')
export const updateUserInfo = (dto) => request.put('/front/user', dto)
export const submitFeedback = (content) =>
  request.post('/front/user/feedback', content, { headers: { 'Content-Type': 'application/json' } })
export const listFeedbacks = (params) => request.get('/front/user/feedbacks', { params })
export const deleteFeedback = (id) => request.delete(`/front/user/feedback/${id}`)
export const comment = (dto) => request.post('/front/user/comment', dto)
export const updateComment = (id, content) => putForm(`/front/user/comment/${id}`, content)
export const deleteComment = (id) => request.delete(`/front/user/comment/${id}`)
export const getBookshelfStatus = (bookId) =>
  request.get('/front/user/bookshelf_status', { params: { bookId } })
export const addBookshelf = (bookId) => request.post(`/front/user/bookshelf/${bookId}`)
export const listBookshelf = (params) => request.get('/front/user/bookshelf', { params })
export const removeBookshelf = (bookId) => request.delete(`/front/user/bookshelf/${bookId}`)
export const listComments = (params) => request.get('/front/user/comments', { params })
