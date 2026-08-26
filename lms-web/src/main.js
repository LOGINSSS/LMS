import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { btnFx } from './directives/btn-fx'
import './styles/main.css'

// 应用入口：挂载根组件、启用路由、注册按钮动效指令
const app = createApp(App)
app.use(router)
app.directive('btn-fx', btnFx)
app.mount('#app')
