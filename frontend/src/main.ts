import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'

// 前端应用启动入口。
// 这里统一注册 Pinia 状态管理、Vue Router 路由和 Element Plus 组件库，
// 然后把 App 挂载到 index.html 中的 #app 节点。
createApp(App)
  .use(createPinia())
  .use(router)
  .use(ElementPlus)
  .mount('#app')
