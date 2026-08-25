import request from '../utils/request'

const ADMIN_PREFIX = '/admin'

const resourceRoutes = {
  books: { page: 'book/page', base: 'book' },
  'book/categories': { page: 'book/category/page', base: 'book/category' },
  authors: { page: 'author/page', base: 'author' },
  users: { page: 'user/page', base: 'user' },
  news: { page: 'news/page', base: 'news' },
  comments: { page: 'book/comment/page', base: 'book/comment' },
  'friend-links': { page: 'home/friend-link/page', base: 'home/friend-link' },
  'home-books': { page: 'home/book/page', base: 'home/book' }
}

function getResourceRoute(resource) {
  return resourceRoutes[resource] || { page: `${resource}/page`, base: resource }
}

export function adminLogin(params) {
  return request.post(`${ADMIN_PREFIX}/login`, params)
}

export function getDashboard() {
  return request.get(`${ADMIN_PREFIX}/dashboard`)
}

export function listResource(resource, params) {
  return request.get(`${ADMIN_PREFIX}/${getResourceRoute(resource).page}`, { params })
}

export function createResource(resource, params) {
  return request.post(`${ADMIN_PREFIX}/${getResourceRoute(resource).base}`, params)
}

export function updateResource(resource, id, params) {
  return request.put(`${ADMIN_PREFIX}/${getResourceRoute(resource).base}/${id}`, params)
}

export function deleteResource(resource, id) {
  return request.delete(`${ADMIN_PREFIX}/${getResourceRoute(resource).base}/${id}`)
}

export function changeResourceStatus(resource, id, status) {
  return request.put(`${ADMIN_PREFIX}/${getResourceRoute(resource).base}/${id}/status`, { status })
}

export function auditBook(id, auditStatus) {
  return request.put(`${ADMIN_PREFIX}/book/${id}/status`, { status: auditStatus })
}

export function auditComment(id, auditStatus) {
  return request.put(`${ADMIN_PREFIX}/book/comment/${id}/audit`, { auditStatus })
}
