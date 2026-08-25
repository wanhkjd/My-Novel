<template>
  <div class="page no-tab">
    <AppHeader title="意见反馈" back />

    <section class="section page-card">
      <div class="section-head">
        <h1 class="section-title">反馈留言</h1>
      </div>
      <div class="form-stack">
        <div class="field">
          <label for="content">反馈内容</label>
          <textarea id="content" v-model.trim="content" maxlength="512" placeholder="请说明问题或建议，也可以留下联系方式"></textarea>
        </div>
        <button class="primary-button" type="button" @click="submit">提交</button>
      </div>
    </section>
  </div>
</template>

<script>
import { reactive, toRefs } from 'vue'
import { ElMessage } from 'element-plus'
import { submitFeedBack } from '@/api/user'
import AppHeader from '@/components/AppHeader.vue'

export default {
  name: 'FeedbackView',
  components: { AppHeader },
  setup() {
    const state = reactive({
      content: ''
    })

    const submit = async () => {
      if (state.content.length < 10) {
        ElMessage.error('反馈内容不能少于 10 个字')
        return
      }
      await submitFeedBack(state.content)
      state.content = ''
      ElMessage.success('提交成功')
    }

    return { ...toRefs(state), submit }
  }
}
</script>
