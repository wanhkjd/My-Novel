const READER_KEY = 'novel_reader_token'
const ADMIN_KEY = 'novel_admin_token'
const NICK_KEY = 'nickName'
const UID_KEY = 'uid'

const read = (k) => localStorage.getItem(k)
const write = (k, v) => localStorage.setItem(k, v)
const clear = (k) => localStorage.removeItem(k)

export const getReaderToken = () => read(READER_KEY)
export const setReaderToken = (t) => write(READER_KEY, t)
export const removeReaderToken = () => clear(READER_KEY)

export const getAdminToken = () => read(ADMIN_KEY)
export const setAdminToken = (t) => write(ADMIN_KEY, t)
export const removeAdminToken = () => clear(ADMIN_KEY)

export const getNickName = () => read(NICK_KEY)
export const setNickName = (n) => write(NICK_KEY, n)
export const removeNickName = () => clear(NICK_KEY)

export const getUid = () => read(UID_KEY)
export const setUid = (u) => write(UID_KEY, u)
export const removeUid = () => clear(UID_KEY)

export function tokenForUrl(url = '') {
  const isAdmin = url.includes('/admin')
  return isAdmin ? getAdminToken() : getReaderToken()
}
