<template>
  <div class="admin-login">
    <section class="admin-login-panel">
      <div class="admin-login-cover">
        <img src="@/assets/images/logo_white.png" alt="小说精品屋" />
        <h1>平台管理端</h1>
        <p>作品、作家、会员与内容运营的统一工作台</p>
      </div>
      <el-form
        ref="formRef"
        class="admin-login-form"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="submit"
      >
        <h2>管理员登录</h2>
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入管理员账号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-button class="admin-login-submit" type="primary" color="#f80" :loading="loading" @click="submit">
          登录后台
        </el-button>
      </el-form>
    </section>
  </div>
</template>

<script>
import '@/assets/styles/admin.css'
import { Lock, User } from '@element-plus/icons-vue'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminLogin } from '@/api/admin'
import { setNickName, setToken, setUid } from '@/utils/auth'

export default {
  name: 'AdminLogin',
  setup() {
    const router = useRouter()
    const formRef = ref()
    const loading = ref(false)
    const form = reactive({
      username: '',
      password: ''
    })
    const rules = {
      username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
      password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
    }

    const submit = async () => {
      try {
        await formRef.value.validate()
      } catch (e) {
        return
      }
      loading.value = true
      try {
        const { data } = await adminLogin(form)
        setToken(data.token)
        if (data.nickName || data.username) {
          setNickName(data.nickName || data.username)
        }
        if (data.uid || data.id) {
          setUid(data.uid || data.id)
        }
        router.push({ name: 'adminDashboard' })
      } catch (e) {
        return
      } finally {
        loading.value = false
      }
    }

    return {
      Lock,
      User,
      form,
      formRef,
      loading,
      rules,
      submit
    }
  }
}
</script>
