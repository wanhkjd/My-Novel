<template>
  <div class="admin-page">
    <section class="admin-settings">
      <div class="admin-section-title">
        <h2>站点配置</h2>
      </div>
      <el-form class="admin-settings-form" :model="form" label-width="120px">
        <el-form-item label="站点名称">
          <el-input v-model="form.siteName" />
        </el-form-item>
        <el-form-item label="备案信息">
          <el-input v-model="form.icp" />
        </el-form-item>
        <el-form-item label="首页公告">
          <el-input v-model="form.notice" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="会员评论">
          <el-switch v-model="form.commentEnabled" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="作家入驻">
          <el-switch v-model="form.authorRegisterEnabled" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" color="#f80" @click="save">保存设置</el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<script>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'

export default {
  name: 'AdminSettings',
  setup() {
    const form = reactive({
      siteName: '小说精品屋',
      icp: '',
      notice: '欢迎来到小说精品屋。',
      commentEnabled: true,
      authorRegisterEnabled: true
    })

    const save = () => {
      ElMessage.success('配置已保存')
    }

    return {
      form,
      save
    }
  }
}
</script>

