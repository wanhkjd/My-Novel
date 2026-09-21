import request from '@/utils/request'
export const listHomeBooks = () => request.get('/front/home/books')
export const listHomeFriendLinks = () => request.get('/front/home/friend_Link/list')
