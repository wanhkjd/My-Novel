<template>
  <div class="admin-page">
    <section class="admin-toolbar">
      <div class="admin-search">
        <el-input v-model="query.keyword" clearable :prefix-icon="Search" :placeholder="config.keywordPlaceholder" />
        <el-select v-if="config.statusOptions" v-model="query.status" clearable placeholder="状态">
          <el-option v-for="item in config.statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" color="#f80" :icon="Search" @click="load">查询</el-button>
      </div>
      <el-button v-if="config.editable" type="primary" color="#f80" :icon="Plus" @click="openDialog()">
        新增{{ config.singleName }}
      </el-button>
    </section>

    <section class="admin-table-panel">
      <el-table v-loading="loading" :data="rows" stripe class="admin-table" empty-text="暂无数据">
        <el-table-column v-for="column in config.columns" :key="column.prop" v-bind="column">
          <template v-if="column.slot === 'status'" #default="{ row }">
            <el-tag :type="statusTag(row[column.prop])">{{ statusText(row[column.prop]) }}</el-tag>
          </template>
          <template v-else-if="column.slot === 'cover'" #default="{ row }">
            <img class="admin-cover" :src="coverUrl(row[column.prop])" :alt="row.bookName || row.title || '封面'" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="config.auditable" text type="success" @click="changeStatus(row, 1)">通过</el-button>
            <el-button text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        background
        layout="prev, pager, next, jumper"
        :page-size="query.pageSize"
        :total="total"
        @current-change="changePage"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="form" label-width="98px">
        <el-form-item v-for="field in config.formFields" :key="field.prop" :label="field.label">
          <el-input
            v-if="field.type === 'textarea'"
            v-model="form[field.prop]"
            type="textarea"
            :rows="4"
            :placeholder="field.placeholder"
          />
          <el-select v-else-if="field.type === 'select'" v-model="form[field.prop]" :placeholder="field.placeholder">
            <el-option v-for="item in field.options" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input v-else v-model="form[field.prop]" :placeholder="field.placeholder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" color="#f80" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { Plus, Search } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref, toRefs, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  auditBook,
  auditComment,
  changeResourceStatus,
  createResource,
  deleteResource,
  listResource,
  updateResource
} from '@/api/admin'

const configs = {
  books: {
    singleName: '小说',
    keywordPlaceholder: '搜索书名、作者',
    editable: true,
    auditable: true,
    columns: [
      { prop: 'picUrl', label: '封面', width: 86, slot: 'cover' },
      { prop: 'bookName', label: '书名', minWidth: 160 },
      { prop: 'authorName', label: '作者', width: 110 },
      { prop: 'categoryName', label: '分类', width: 100 },
      { prop: 'wordCount', label: '字数', width: 100 },
      { prop: 'visitCount', label: '点击', width: 100 },
      { prop: 'status', label: '状态', width: 100, slot: 'status' },
      { prop: 'updateTime', label: '更新时间', width: 170 }
    ],
    formFields: [
      { prop: 'bookName', label: '书名', placeholder: '请输入书名' },
      { prop: 'authorName', label: '作者', placeholder: '请输入作者' },
      { prop: 'categoryName', label: '分类', placeholder: '请输入分类' },
      { prop: 'bookDesc', label: '简介', type: 'textarea', placeholder: '请输入简介' }
    ]
  },
  'book/categories': {
    singleName: '分类',
    keywordPlaceholder: '搜索分类名称',
    editable: true,
    columns: [
      { prop: 'name', label: '分类名称', minWidth: 160 },
      { prop: 'workDirectionText', label: '频道', width: 120 },
      { prop: 'sort', label: '排序', width: 100 },
      { prop: 'status', label: '状态', width: 110, slot: 'status' },
      { prop: 'updateTime', label: '更新时间', width: 170 }
    ],
    formFields: [
      { prop: 'name', label: '分类名称', placeholder: '请输入分类名称' },
      {
        prop: 'workDirection',
        label: '频道',
        type: 'select',
        placeholder: '请选择频道',
        options: [
          { label: '男频', value: 0 },
          { label: '女频', value: 1 }
        ]
      },
      { prop: 'sort', label: '排序', placeholder: '请输入排序值' }
    ]
  },
  authors: {
    singleName: '作家',
    keywordPlaceholder: '搜索笔名、手机号',
    editable: true,
    columns: [
      { prop: 'penName', label: '笔名', minWidth: 150 },
      { prop: 'telPhone', label: '手机号', width: 130 },
      { prop: 'bookCount', label: '作品数', width: 100 },
      { prop: 'wordCount', label: '总字数', width: 110 },
      { prop: 'status', label: '状态', width: 110, slot: 'status' },
      { prop: 'createTime', label: '入驻时间', width: 170 }
    ],
    formFields: [
      { prop: 'penName', label: '笔名', placeholder: '请输入笔名' },
      { prop: 'telPhone', label: '手机号', placeholder: '请输入手机号' },
      { prop: 'inviteCode', label: '邀请码', placeholder: '请输入邀请码' }
    ]
  },
  users: {
    singleName: '用户',
    keywordPlaceholder: '搜索用户名、昵称',
    editable: true,
    columns: [
      { prop: 'username', label: '用户名', minWidth: 150 },
      { prop: 'nickName', label: '昵称', width: 130 },
      { prop: 'bookshelfCount', label: '书架', width: 90 },
      { prop: 'commentCount', label: '评论', width: 90 },
      { prop: 'status', label: '状态', width: 110, slot: 'status' },
      { prop: 'createTime', label: '注册时间', width: 170 }
    ],
    formFields: [
      { prop: 'username', label: '用户名', placeholder: '请输入用户名' },
      { prop: 'nickName', label: '昵称', placeholder: '请输入昵称' },
      { prop: 'userPhoto', label: '头像', placeholder: '请输入头像地址' }
    ]
  },
  news: {
    singleName: '新闻',
    keywordPlaceholder: '搜索标题、来源',
    editable: true,
    columns: [
      { prop: 'title', label: '标题', minWidth: 220 },
      { prop: 'sourceName', label: '来源', width: 140 },
      { prop: 'status', label: '状态', width: 110, slot: 'status' },
      { prop: 'updateTime', label: '发布时间', width: 170 }
    ],
    formFields: [
      { prop: 'title', label: '标题', placeholder: '请输入标题' },
      { prop: 'sourceName', label: '来源', placeholder: '请输入来源' },
      { prop: 'content', label: '正文', type: 'textarea', placeholder: '请输入正文' }
    ]
  },
  comments: {
    singleName: '评论',
    keywordPlaceholder: '搜索评论内容、用户',
    editable: false,
    auditable: true,
    columns: [
      { prop: 'commentUser', label: '用户', width: 140 },
      { prop: 'commentBook', label: '作品', width: 160 },
      { prop: 'commentContent', label: '评论内容', minWidth: 260 },
      { prop: 'status', label: '状态', width: 110, slot: 'status' },
      { prop: 'commentTime', label: '评论时间', width: 170 }
    ],
    formFields: []
  },
  'home-books': {
    singleName: '首页推荐',
    keywordPlaceholder: '搜索推荐作品',
    editable: true,
    columns: [
      { prop: 'bookName', label: '书名', minWidth: 170 },
      { prop: 'authorName', label: '作者', width: 130 },
      { prop: 'type', label: '推荐类型', width: 110 },
      { prop: 'sort', label: '排序', width: 90 },
      { prop: 'updateTime', label: '更新时间', width: 170 }
    ],
    formFields: [
      { prop: 'bookId', label: '小说ID', placeholder: '请输入小说ID' },
      {
        prop: 'type',
        label: '推荐类型',
        type: 'select',
        placeholder: '请选择推荐类型',
        options: [
          { label: '轮播图', value: 0 },
          { label: '顶部栏', value: 1 },
          { label: '本周强推', value: 2 },
          { label: '热门推荐', value: 3 },
          { label: '精品推荐', value: 4 }
        ]
      },
      { prop: 'sort', label: '排序', placeholder: '请输入排序值' }
    ]
  },
  'friend-links': {
    singleName: '友情链接',
    keywordPlaceholder: '搜索站点名称',
    editable: true,
    columns: [
      { prop: 'linkName', label: '站点名称', minWidth: 180 },
      { prop: 'linkUrl', label: '链接地址', minWidth: 240 },
      { prop: 'sort', label: '排序', width: 90 },
      { prop: 'status', label: '状态', width: 110, slot: 'status' },
      { prop: 'updateTime', label: '更新时间', width: 170 }
    ],
    formFields: [
      { prop: 'linkName', label: '站点名称', placeholder: '请输入站点名称' },
      { prop: 'linkUrl', label: '链接地址', placeholder: '请输入链接地址' },
      { prop: 'sort', label: '排序', placeholder: '请输入排序值' }
    ]
  }
}

const sampleRows = {
  books: [
    { id: 1, bookName: '星河旅人', authorName: '青砚', categoryName: '玄幻', wordCount: '82万', visitCount: 18320, status: 1, updateTime: '2026-07-21 09:30', picUrl: '' },
    { id: 2, bookName: '云端书局', authorName: '白榆', categoryName: '都市', wordCount: '41万', visitCount: 8902, status: 0, updateTime: '2026-07-20 22:12', picUrl: '' }
  ],
  'book/categories': [
    { id: 1, name: '玄幻奇幻', workDirectionText: '男频', sort: 1, status: 1, updateTime: '2026-07-18 12:20' },
    { id: 2, name: '现代言情', workDirectionText: '女频', sort: 2, status: 1, updateTime: '2026-07-18 12:20' }
  ],
  authors: [
    { id: 1, penName: '青砚', telPhone: '13800000000', bookCount: 3, wordCount: '196万', status: 1, createTime: '2026-06-02 10:15' },
    { id: 2, penName: '白榆', telPhone: '13900000000', bookCount: 1, wordCount: '41万', status: 0, createTime: '2026-07-10 15:21' }
  ],
  users: [
    { id: 1, username: 'reader001', nickName: '夜读人', bookshelfCount: 18, commentCount: 7, status: 1, createTime: '2026-05-11 08:30' },
    { id: 2, username: 'reader002', nickName: '纸页之间', bookshelfCount: 6, commentCount: 2, status: 1, createTime: '2026-07-02 19:44' }
  ],
  news: [
    { id: 1, title: '七月重点作品推荐上线', sourceName: '平台运营', status: 1, updateTime: '2026-07-20 09:00' }
  ],
  comments: [
    { id: 1, commentUser: '夜读人', commentBook: '星河旅人', commentContent: '更新节奏很稳，故事线也清楚。', status: 1, commentTime: '2026-07-21 10:11' }
  ],
  'home-books': [
    { id: 1, bookId: 1, bookName: '星河旅人', authorName: '青砚', type: 3, sort: 1, updateTime: '2026-07-21 09:30' }
  ],
  'friend-links': [
    { id: 1, linkName: '小说精品屋', linkUrl: 'https://www.xxyopen.com', sort: 1, status: 1, updateTime: '2026-07-16 11:20' }
  ]
}

void sampleRows

export default {
  name: 'AdminResourceManage',
  setup() {
    const route = useRoute()
    const formRef = ref()
    const state = reactive({
      rows: [],
      total: 0,
      loading: false,
      saving: false,
      dialogVisible: false,
      form: {},
      query: {
        pageNum: 1,
        pageSize: 10,
        keyword: '',
        status: ''
      }
    })

    const resource = computed(() => route.meta.resource)
    const config = computed(() => configs[resource.value] || configs.books)
    const dialogTitle = computed(() => `${state.form.id ? '编辑' : '新增'}${config.value.singleName}`)

    const load = async () => {
      state.loading = true
      try {
        const { data } = await listResource(resource.value, state.query)
        state.rows = data.list || data.records || data || []
        state.total = Number(data.total || state.rows.length)
      } catch (e) {
        state.rows = []
        state.total = 0
      } finally {
        state.loading = false
      }
    }

    const changePage = (pageNum) => {
      state.query.pageNum = pageNum
      load()
    }

    const openDialog = (row) => {
      state.form = row ? { ...row } : {}
      state.dialogVisible = true
    }

    const save = async () => {
      state.saving = true
      try {
        if (state.form.id) {
          await updateResource(resource.value, state.form.id, state.form)
        } else {
          await createResource(resource.value, state.form)
        }
        ElMessage.success('保存成功')
        state.dialogVisible = false
        load()
      } catch (e) {
        return
      } finally {
        state.saving = false
      }
    }

    const remove = async (row) => {
      try {
        await ElMessageBox.confirm(`确认删除该${config.value.singleName}？`, '提示', {
          type: 'warning'
        })
      } catch (e) {
        return
      }
      try {
        await deleteResource(resource.value, row.id)
        ElMessage.success('删除成功')
      } catch (e) {
        return
      }
      state.rows = state.rows.filter((item) => item.id !== row.id)
      state.total = state.rows.length
    }

    const changeStatus = async (row, status) => {
      try {
        if (resource.value === 'books') {
          await auditBook(row.id, status)
        } else if (resource.value === 'comments') {
          await auditComment(row.id, status)
        } else {
          await changeResourceStatus(resource.value, row.id, status)
        }
        ElMessage.success('状态已更新')
      } catch (e) {
        return
      }
      row.status = status
    }

    const statusText = (status) => {
      if (status === 1 || status === '1') {
        return '正常'
      }
      if (status === 2 || status === '2') {
        return '禁用'
      }
      return '待审核'
    }

    const statusTag = (status) => {
      if (status === 1 || status === '1') {
        return 'success'
      }
      if (status === 2 || status === '2') {
        return 'danger'
      }
      return 'warning'
    }

    const coverUrl = (url) => {
      if (!url) {
        return require('@/assets/images/smlcover.png')
      }
      if (/^https?:\/\//.test(url)) {
        return url
      }
      return `${process.env.VUE_APP_BASE_IMG_URL || ''}${url}`
    }

    watch(resource, () => {
      state.query.pageNum = 1
      state.query.keyword = ''
      state.query.status = ''
      load()
    })

    onMounted(load)

    return {
      Plus,
      Search,
      ...toRefs(state),
      config,
      dialogTitle,
      formRef,
      load,
      changePage,
      openDialog,
      save,
      remove,
      changeStatus,
      statusText,
      statusTag,
      coverUrl
    }
  }
}
</script>
