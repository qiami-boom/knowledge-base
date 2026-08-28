import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(router)          // 把路由安装到 Vue 应用里
app.mount('#app')