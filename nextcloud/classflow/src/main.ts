import { createApp } from 'vue'
import App from './App.vue'

import './style.scss'

const root = document.getElementById('classflow-app')

if (root) {
	try {
		createApp(App).mount(root)
	} catch {
		root.innerHTML = '<main class="classflow-shell"><div class="notice error" role="alert">ClassFlow 無法啟動，請重新整理頁面；若問題持續，請查看瀏覽器主控台。</div></main>'
	}
}
