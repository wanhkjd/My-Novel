import request from '../utils/request'

const ADMIN_PREFIX = '/admin'

export function adminLogin(params) {
  return request.post(`${ADMIN_PREFIX}/login`, params)
}

export function getDashboard() {
  return request.get(`${ADMIN_PREFIX}/dashboard`)
}

export function listResource(resource, params) {
  return request.get(`${ADMIN_PREFIX}/${resource}`, { params })
}

export function createResource(resource, params) {
  return request.post(`${ADMIN_PREFIX}/${resource}`, params)
}

export function updateResource(resource, id, params) {
  return request.put(`${ADMIN_PREFIX}/${resource}/${id}`, params)
}

export function deleteResource(resource, id) {
  return request.delete(`${ADMIN_PREFIX}/${resource}/${id}`)
}

export function changeResourceStatus(resource, id, status) {
  return request.put(`${ADMIN_PREFIX}/${resource}/${id}/status`, { status })
}

export function auditBook(id, auditStatus) {
  return request.put(`${ADMIN_PREFIX}/books/${id}/audit`, { auditStatus })
}
