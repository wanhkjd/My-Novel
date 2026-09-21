import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from '@/utils/request'
import '@/styles/tokens.css'
import '@/styles/global.css'

// token 失效(A0230)时由 router 执行跳转，解耦 request 与 router
setUnauthorizedHandler((path) => router.push(path).catch(() => {}))

const app = createApp(App)
for (const [key, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, comp)
}
app.config.errorHandler = (err) => {
  if (err && (err.isApiError || err.name === 'ApiError')) return
  console.error(err)
}
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
