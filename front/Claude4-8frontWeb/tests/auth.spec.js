import { describe, it, expect, beforeEach } from 'vitest'
import {
  getReaderToken, setReaderToken, removeReaderToken,
  getAdminToken, setAdminToken, tokenForUrl
} from '@/utils/auth'

describe('auth', () => {
  beforeEach(() => localStorage.clear())

  it('reader token roundtrip', () => {
    expect(getReaderToken()).toBeNull()
    setReaderToken('R1')
    expect(getReaderToken()).toBe('R1')
    removeReaderToken()
    expect(getReaderToken()).toBeNull()
  })

  it('tokenForUrl picks the right slot', () => {
    setReaderToken('R1')
    setAdminToken('A1')
    expect(tokenForUrl('/front/book/1')).toBe('R1')
    expect(tokenForUrl('/admin/dashboard')).toBe('A1')
  })

  it('tokenForUrl returns null when slot empty', () => {
    expect(tokenForUrl('/front/user')).toBeNull()
  })
})
