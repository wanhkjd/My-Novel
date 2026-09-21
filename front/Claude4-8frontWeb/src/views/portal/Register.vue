<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2 class="auth-title">注册墨书</h2>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
        <el-form-item label="手机号" prop="username">
          <el-input v-model="form.username" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请设置密码" show-password />
        </el-form-item>
        <el-form-item label="图形验证码" prop="velCode">
          <div class="vel-row">
            <el-input v-model="form.velCode" placeholder="4 位验证码" maxlength="4" />
            <img class="vel-img" :src="velImg" alt="验证码" title="点击刷新" @click="loadCode" />
          </div>
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">注册</el-button>
      </el-form>
      <p class="switch">已有账号？<router-link to="/login">去登录</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getImgVerifyCode } from '@/api/resource'
import { register } from '@/api/user'

const router = useRouter()
const formRef = ref(), loading = ref(false)
const velImg = ref('')
const form = reactive({ username: '', password: '', velCode: '', sessionId: '' })
const rules = {
  username: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请设置密码', trigger: 'blur' }],
  velCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{4}$/, message: '验证码为 4 位数字', trigger: 'blur' }
  ]
}
async function loadCode() {
  try {
    const { data } = await getImgVerifyCode()
    velImg.value = 'data:image/png;base64,' + data.img
    form.sessionId = data.sessionId
  } catch (e) { /* ignore */ }
}
async function submit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!form.sessionId) { ElMessage.warning('请先获取验证码'); return }
    loading.value = true
    try {
      await register({ ...form })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch (e) { loadCode() } finally { loading.value = false }
  })
}
onMounted(loadCode)
</script>

<style scoped>
.auth-page { display: flex; justify-content: center; padding: 60px 16px; }
.auth-card { width: 400px; max-width: 100%; padding: var(--sp-6); }
.auth-title { text-align: center; font-size: 24px; margin-bottom: var(--sp-5); }
.vel-row { display: flex; gap: 12px; width: 100%; }
.vel-img { height: 40px; border-radius: var(--radius-sm); cursor: pointer; border: 1px solid var(--border); }
.submit-btn { width: 100%; margin-top: var(--sp-2); }
.switch { text-align: center; margin-top: var(--sp-4); color: var(--text-2); font-size: 14px; }
.switch a { color: var(--brand); }
</style>
