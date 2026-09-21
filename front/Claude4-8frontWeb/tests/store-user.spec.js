import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/user', () => ({
  login: vi.fn(() => Promise.resolve({ data: { uid: 5, nickName: '墨客', token: 'T1' } })),
  getUserInfo: vi.fn(() => Promise.resolve({ data: { nickName: '墨客', userPhoto: '/a.png', userSex: 1 } }))
}))

import { useUserStore } from '@/stores/user'
import { getReaderToken } from '@/utils/auth'

describe('user store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

  it('loginAction stores token/nick/uid and sets reader slot', async () => {
    const s = useUserStore()
    await s.loginAction({ username: '18888888888', password: '123456' })
    expect(s.token).toBe('T1')
    expect(s.nickName).toBe('墨客')
    expect(s.isLoggedIn).toBe(true)
    expect(getReaderToken()).toBe('T1')
  })

  it('logout clears everything', async () => {
    const s = useUserStore()
    await s.loginAction({ username: '18888888888', password: '123456' })
    s.logout()
    expect(s.token).toBeNull()
    expect(s.isLoggedIn).toBe(false)
    expect(getReaderToken()).toBeNull()
  })
})
