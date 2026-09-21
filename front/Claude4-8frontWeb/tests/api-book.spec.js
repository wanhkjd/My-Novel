import { describe, it, expect, vi, beforeEach } from 'vitest'

const calls = []
vi.mock('@/utils/request', () => ({
  default: {
    get: (url, cfg) => { calls.push(['get', url, cfg]); return Promise.resolve({ data: null }) },
    post: (url, data, cfg) => { calls.push(['post', url, data, cfg]); return Promise.resolve({ data: null }) }
  }
}))

import * as bookApi from '@/api/book'

describe('book api paths', () => {
  beforeEach(() => { calls.length = 0 })

  it('getBookById hits /front/book/{id}', () => {
    bookApi.getBookById(42)
    expect(calls[0][0]).toBe('get')
    expect(calls[0][1]).toBe('/front/book/42')
  })
  it('getBookContent hits /front/book/content/{chapterId}', () => {
    bookApi.getBookContent(7)
    expect(calls[0][1]).toBe('/front/book/content/7')
  })
  it('listCategory passes workDirection as query', () => {
    bookApi.listCategory(0)
    expect(calls[0][1]).toBe('/front/book/category/list')
    expect(calls[0][2]).toEqual({ params: { workDirection: 0 } })
  })
})
