<template>
  <div class="container user-page" v-loading="loading">
    <h3 class="section-title">个人资料</h3>
    <div class="profile card">
      <el-form :model="form" label-width="80px">
        <el-form-item label="头像">
          <el-upload :show-file-list="false" :before-upload="onUpload" accept="image/*">
            <img v-if="form.userPhoto" :src="photoUrl" class="avatar" />
            <div v-else class="avatar avatar-empty">上传</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickName" maxlength="10" style="max-width:300px" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.userSex">
            <el-radio :value="0">男</el-radio>
            <el-radio :value="1">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-button type="primary" @click="save">保存修改</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserInfo, updateUserInfo } from '@/api/user'
import { uploadImage } from '@/api/resource'
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ nickName: '', userPhoto: '', userSex: 0 })
const IMG = import.meta.env.VITE_BASE_IMG_URL || ''
const photoUrl = computed(() => form.userPhoto ? IMG + form.userPhoto : '')
async function load() {
  loading.value = true
  try {
    const { data } = await getUserInfo()
    form.nickName = data.nickName || ''
    form.userPhoto = data.userPhoto || ''
    form.userSex = data.userSex ?? 0
  } catch (e) {} finally { loading.value = false }
}
async function onUpload(file) {
  try { const { data } = await uploadImage(file); form.userPhoto = data; ElMessage.success('头像已上传') } catch (e) {}
  return false
}
async function save() {
  try {
    // 后端对 userPhoto 有正则校验，空值不传该字段
    const payload = { nickName: form.nickName, userSex: form.userSex }
    if (form.userPhoto) payload.userPhoto = form.userPhoto
    await updateUserInfo(payload)
    ElMessage.success('资料已更新')
    userStore.nickName = form.nickName
    await userStore.fetchUserInfo()
  } catch (e) {}
}
onMounted(load)
</script>

<style scoped>
.user-page { padding-top: var(--sp-5); }
.profile { padding: var(--sp-6); max-width: 560px; }
.avatar { width: 84px; height: 84px; border-radius: 50%; object-fit: cover; }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--brand-soft); color: var(--brand); border: 1px dashed var(--brand); }
</style>
