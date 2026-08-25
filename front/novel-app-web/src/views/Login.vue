<template>
  <div class="page no-tab">
    <AppHeader title="登录" back />

    <section class="section page-card">
      <div class="section-head">
        <h1 class="section-title">登录小说精品屋</h1>
      </div>
      <div class="form-stack">
        <div class="field">
          <label for="username">手机号</label>
          <input id="username" v-model.trim="username" type="tel" autocomplete="tel" inputmode="tel" placeholder="请输入手机号" />
        </div>
        <div class="field">
          <label for="password">密码</label>
          <input id="password" v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        </div>
        <button class="primary-button" type="button" @click="loginUser">登录</button>
      </div>
      <p class="auth-switch">
        还没有账号？<router-link :to="{ name: 'register' }">立即注册</router-link>
      </p>
    </section>
  </div>
</template>

<script>
import { reactive, toRefs } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/user'
import { setNickName, setToken, setUid } from '@/utils/auth'
import AppHeader from '@/components/AppHeader.vue'

export default {
  name: 'LoginView',
  components: { AppHeader },
  setup() {
    const router = useRouter()
    const route = useRoute()
    const state = reactive({
      username: '',
      password: ''
    })

    const loginUser = async () => {
      if (!/^1[3-9]\d{9}$/.test(state.username)) {
        ElMessage.error('请输入正确的手机号')
        return
      }
      if (!state.password) {
        ElMessage.error('请输入密码')
        return
      }
      const { data } = await login({ username: state.username, password: state.password })
      setToken(data.token)
      setUid(data.uid)
      setNickName(data.nickName || state.username)
      router.push(route.query.redirect || { name: 'home' })
    }

    return { ...toRefs(state), loginUser }
  }
}
</script>
