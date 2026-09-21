<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2 class="auth-title">登录墨书</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="username">
          <el-input v-model="form.username" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">登录</el-button>
      </el-form>
      <p class="switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter(), route = useRoute()
const userStore = useUserStore()
const formRef = ref(), loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
async function submit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.loginAction({ ...form })
      ElMessage.success('登录成功')
      router.push(route.query.redirect || '/home')
    } catch (e) { /* 拦截器已提示 */ } finally { loading.value = false }
  })
}
</script>

<style scoped>
.auth-page { display: flex; justify-content: center; padding: 60px 16px; }
.auth-card { width: 400px; max-width: 100%; padding: var(--sp-6); }
.auth-title { text-align: center; font-size: 24px; margin-bottom: var(--sp-5); }
.submit-btn { width: 100%; margin-top: var(--sp-2); }
.switch { text-align: center; margin-top: var(--sp-4); color: var(--text-2); font-size: 14px; }
.switch a { color: var(--brand); }
</style>
