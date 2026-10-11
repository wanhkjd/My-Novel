import { afterEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import request, { setUnauthorizedHandler } from '@/utils/request'

vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

afterEach(() => {
  setUnauthorizedHandler(null)
  vi.clearAllMocks()
})

describe('banned user response', () => {
  it('rejects the operation and displays the ban reason without treating it as token expiry', async () => {
    const redirect = vi.fn()
    setUnauthorizedHandler(redirect)
    const response = request.get('/front/user/bookshelf', {
      adapter: async config => ({
        config,
        status: 200,
        statusText: 'OK',
        headers: {},
        data: { code: 'A0202', message: '用户账号已被封禁', data: null }
      })
    })

    await expect(response).rejects.toMatchObject({
      isApiError: true,
      code: 'A0202',
      message: '用户账号已被封禁'
    })
    expect(ElMessage.error).toHaveBeenCalledWith('用户账号已被封禁')
    expect(redirect).not.toHaveBeenCalled()
  })
})
