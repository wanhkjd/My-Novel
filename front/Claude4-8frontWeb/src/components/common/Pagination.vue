<template>
  <div v-if="total > 0" class="pagination-wrap">
    <el-pagination
      background layout="prev, pager, next"
      :total="total" :current-page="pageNum" :page-size="pageSize"
      @current-change="onChange" />
  </div>
</template>

<script setup>
const props = defineProps({
  total: { type: Number, default: 0 },
  pageNum: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 }
})
const emit = defineEmits(['update:pageNum'])
const onChange = (p) => emit('update:pageNum', p)
</script>

<style scoped>
.pagination-wrap { display: flex; justify-content: center; padding: var(--sp-5) 0; }
</style>
