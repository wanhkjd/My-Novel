import { describe, it, expect } from 'vitest'
import { handleSuccess, buildApiError, SUCCESS_CODE } from '@/utils/request'

describe('request interceptors', () => {
  it('handleSuccess returns body on success code', () => {
    const body = { code: SUCCESS_CODE, message: 'ok', data: { id: 1 } }
    expect(handleSuccess({ data: body })).toEqual(body)
  })

  it('handleSuccess throws ApiError on business error', () => {
    const body = { code: 'B0001', message: '业务失败', data: null }
    expect(() => handleSuccess({ data: body })).toThrowError()
    try { handleSuccess({ data: body }) }
    catch (e) { expect(e.isApiError).toBe(true); expect(e.code).toBe('B0001') }
  })

  it('buildApiError carries fields', () => {
    const e = buildApiError({ code: 'X', message: 'm', data: 1 })
    expect(e.isApiError).toBe(true)
    expect(e.code).toBe('X')
    expect(e.message).toBe('m')
    expect(e.name).toBe('ApiError')
  })
})
