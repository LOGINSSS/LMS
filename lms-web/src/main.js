import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/main.css'

// 应用入口：挂载根组件并启用路由
createApp(App).use(router).mount('#app')
