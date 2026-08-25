<template>
  <div class="page no-tab">
    <AppHeader title="注册" back />

    <section class="section page-card">
      <div class="section-head">
        <h1 class="section-title">创建账号</h1>
      </div>
      <div class="form-stack">
        <div class="field">
          <label for="username">手机号</label>
          <input id="username" v-model.trim="username" type="tel" autocomplete="tel" inputmode="tel" placeholder="请输入手机号" />
        </div>
        <div class="field">
          <label for="password">密码</label>
          <input id="password" v-model="password" type="password" autocomplete="new-password" placeholder="6-20 位字母或数字" />
        </div>
        <div class="field">
          <label for="velCode">验证码</label>
          <div class="captcha-row">
            <input id="velCode" v-model.trim="velCode" type="text" inputmode="numeric" maxlength="4" placeholder="4 位验证码" />
            <img :src="imgVerifyCode" alt="验证码" @click="loadImgVerifyCode" />
          </div>
        </div>
        <button class="primary-button" type="button" @click="registerUser">注册</button>
      </div>
      <p class="auth-switch">
        已有账号？<router-link :to="{ name: 'login' }">立即登录</router-link>
      </p>
    </section>
  </div>
</template>

<script>
import { onMounted, reactive, toRefs } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getImgVerifyCode } from '@/api/resource'
import { register } from '@/api/user'
import { setNickName, setToken, setUid } from '@/utils/auth'
import AppHeader from '@/components/AppHeader.vue'

export default {
  name: 'RegisterView',
  components: { AppHeader },
  setup() {
    const router = useRouter()
    const state = reactive({
      imgVerifyCode: '',
      sessionId: '',
      username: '',
      password: '',
      velCode: ''
    })

    const loadImgVerifyCode = async () => {
      const { data } = await getImgVerifyCode()
      state.imgVerifyCode = `data:image/png;base64,${data.img}`
      state.sessionId = data.sessionId
    }

    const registerUser = async () => {
      if (!/^1[3-9]\d{9}$/.test(state.username)) {
        ElMessage.error('请输入正确的手机号')
        return
      }
      if (!state.password || state.password.length < 6) {
        ElMessage.error('密码至少 6 位')
        return
      }
      if (!/^\d{4}$/.test(state.velCode)) {
        ElMessage.error('请输入 4 位验证码')
        return
      }
      const { data } = await register({
        username: state.username,
        password: state.password,
        velCode: state.velCode,
        sessionId: state.sessionId
      })
      setToken(data.token)
      setUid(data.uid)
      setNickName(data.nickName || state.username)
      router.push({ name: 'home' })
    }

    onMounted(loadImgVerifyCode)

    return { ...toRefs(state), loadImgVerifyCode, registerUser }
  }
}
</script>
