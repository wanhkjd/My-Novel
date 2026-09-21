import request from '@/utils/request'
export const listCategory = (workDirection) =>
  request.get('/front/book/category/list', { params: { workDirection } })
export const getBookById = (id) => request.get(`/front/book/${id}`)
export const addVisitCount = (bookId) =>
  request.post('/front/book/visit', null, { params: { bookId } })
export const getLastChapterAbout = (bookId) =>
  request.get('/front/book/last_chapter/about', { params: { bookId } })
export const listRecBooks = (bookId) =>
  request.get('/front/book/rec_list', { params: { bookId } })
export const listChapters = (bookId) =>
  request.get('/front/book/chapter/list', { params: { bookId } })
export const getBookContent = (chapterId) => request.get(`/front/book/content/${chapterId}`)
export const getPreChapterId = (chapterId) => request.get(`/front/book/pre_chapter_id/${chapterId}`)
export const getNextChapterId = (chapterId) => request.get(`/front/book/next_chapter_id/${chapterId}`)
export const listVisitRankBooks = () => request.get('/front/book/visit_rank')
export const listNewestRankBooks = () => request.get('/front/book/newest_rank')
export const listUpdateRankBooks = () => request.get('/front/book/update_rank')
export const listNewestComments = (bookId) =>
  request.get('/front/book/comment/newest_list', { params: { bookId } })
