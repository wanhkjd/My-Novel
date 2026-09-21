import request from '@/utils/request'
export const listLatestNews = () => request.get('/front/news/latest_list')
export const getNewsById = (id) => request.get(`/front/news/${id}`)
