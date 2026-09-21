import { defineStore } from 'pinia'
import { login, getUserInfo } from '@/api/user'
import {
  getReaderToken, setReaderToken, removeReaderToken,
  getNickName, setNickName, removeNickName,
  getUid, setUid, removeUid
} from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getReaderToken(),
    nickName: getNickName(),
    uid: getUid(),
    userInfo: null
  }),
  getters: { isLoggedIn: (s) => !!s.token },
  actions: {
    async loginAction(dto) {
      const { data } = await login(dto)
      this.token = data.token
      this.nickName = data.nickName
      this.uid = String(data.uid)
      setReaderToken(data.token); setNickName(data.nickName); setUid(String(data.uid))
      return data
    },
    async fetchUserInfo() {
      const { data } = await getUserInfo()
      this.userInfo = data
      if (data && data.nickName) { this.nickName = data.nickName; setNickName(data.nickName) }
      return data
    },
    logout() {
      this.token = null; this.nickName = null; this.uid = null; this.userInfo = null
      removeReaderToken(); removeNickName(); removeUid()
    },
    hydrate() {
      this.token = getReaderToken(); this.nickName = getNickName(); this.uid = getUid()
    }
  }
})
