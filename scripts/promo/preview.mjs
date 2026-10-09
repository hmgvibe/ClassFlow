// Isolated demo of the production Vue components. This is not a Nextcloud server.
import { createServer } from 'node:http'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

const root = fileURLToPath(new URL('../../', import.meta.url))
const output = resolve(root, 'artifacts/promo')
await mkdir(output, { recursive: true })
const at = (hour, day = 0) => { const date = new Date(); date.setDate(date.getDate() + day); date.setHours(hour, 0, 0, 0); return date.getTime() }
const uuid = (n) => `11111111-1111-4111-8111-${String(n).padStart(12, '0')}`
const course = (n, name, teacher, room, colorKey) => ({ id: uuid(n), name, teacher, room, colorKey, notes: '', version: 1, updatedAt: 0 })
let state = {
	courses: [course(1, '高等数学', '陈老师', '教学楼 A201', 0), course(2, '大学英语', '林老师', '教学楼 B302', 1), course(3, '程序设计', '王老师', '计算机教室', 3)],
	slots: Array.from({ length: 15 }, (_, n) => ({ id: uuid(n + 10), courseId: uuid(n % 3 + 1), dayOfWeek: Math.floor(n / 3) + 1, startMinutes: [480, 600, 840][n % 3], endMinutes: [570, 690, 930][n % 3], roomOverride: '', version: 1, updatedAt: 0 })),
	agendaItems: [
		{ id: uuid(30), type: 'homework', title: '完成数学第二章练习', occursAt: at(17), endsAt: null, allDay: false, status: 'pending', notes: '整理不熟悉的题型', reminderAt: null, linkedSlotIds: [], version: 1, updatedAt: 0 },
		{ id: uuid(31), type: 'exam', title: '数学期中考试', occursAt: at(9, 3), endsAt: at(11, 3), allDay: false, status: 'pending', notes: '第一章至第三章', reminderAt: null, linkedSlotIds: [], version: 1, updatedAt: 0 },
		{ id: uuid(32), type: 'activity', title: '英语小组讨论', occursAt: at(19), endsAt: at(20), allDay: false, status: 'pending', notes: '', reminderAt: null, linkedSlotIds: [], version: 1, updatedAt: 0 },
	],
	studyPlans: [{ id: uuid(40), title: '整理课堂笔记', startsAt: at(9), endsAt: at(10), linkedCourseId: uuid(1), linkedAgendaId: null, notes: '梳理关键公式与例题', version: 1, updatedAt: 0 }], serverTime: Date.now(),
}
const css = `:root{--color-main-background:#fff;--color-main-text:#202727;--color-text-maxcontrast:#556465;--color-border:#e0e5e5;--color-border-maxcontrast:#8c9999;--color-primary-element:#087d73;--color-primary-element-light:#dcefeb;--color-primary-element-text:#fff;--color-background-hover:#eef3f3;--color-error-text:#b42318;--color-error-hover:#ffedea;--border-radius-large:12px;--border-radius-element:8px;--default-font-size:15px;--default-clickable-area:44px;--font-face:system-ui}*{box-sizing:border-box}body{margin:0;font:15px system-ui;color:var(--color-main-text);background:var(--color-main-background)}button,input,select,textarea{font:inherit}.button-vue{border:0;border-radius:8px;padding:0 16px;min-height:44px}.button-vue--vue-primary{color:#fff}`
const language = (code) => `window.OC={webroot:'',appswebroots:{classflow:'/apps/classflow'},config:{version:'35.0.0',modRewriteWorking:true},getLanguage:()=> '${code}',getLocale:()=> '${code.replace('-', '_')}',currentUser:'demo',requestToken:'demo',isUserAdmin:()=>false};window._oc_config=OC.config;window.oc_requesttoken='demo';window.oc_webroot='';`
const html = (code) => `<!doctype html><html lang="${code}"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ClassFlow · 学习安排</title><style>${css}body{font-family:"Microsoft YaHei",system-ui}#classflow-app{max-width:1320px;margin:0 auto}.classflow-app{padding-top:24px}</style><link rel="stylesheet" href="/css/classflow-main.css"><script>${language(code)}</script><div id="classflow-app"></div><script src="/js/classflow-main.js"></script></html>`
createServer(async (request, response) => {
	try {
		const url = new URL(request.url, 'http://127.0.0.1:4179')
		if (url.pathname.includes('/api/v1/state')) {
			response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ ocs: { data: state } }))
		} else if (url.pathname.includes('/api/v1/sync') && request.method === 'POST') {
			let body = ''; for await (const chunk of request) body += chunk
			const { mutations } = JSON.parse(body)
			for (const mutation of mutations) {
				const collection = { course: 'courses', slot: 'slots', agenda: 'agendaItems', study: 'studyPlans' }[mutation.entityType]
				if (!collection) throw new Error('Unknown entity')
				state[collection] = state[collection].filter(item => item.id !== mutation.entityId)
				if (mutation.operation !== 'delete') state[collection].push({ ...mutation.payload, version: mutation.baseVersion + 1, updatedAt: Date.now() })
			}
			await writeFile(resolve(output, 'web-demo-state.json'), JSON.stringify(state, null, 2))
			response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ ocs: { data: { state, acceptedOperationIds: mutations.map(m => m.operationId), conflicts: [] } } }))
		} else if (['/css/classflow-main.css', '/js/classflow-main.js'].includes(url.pathname)) {
			response.setHeader('Content-Type', url.pathname.endsWith('.css') ? 'text/css' : 'application/javascript'); response.end(await readFile(resolve(root, 'nextcloud/classflow' + url.pathname)))
		} else if (url.pathname === '/') {
			response.setHeader('Content-Type', 'text/html;charset=utf-8'); response.end(html(url.searchParams.get('lang') ?? 'zh-CN'))
		} else { response.writeHead(404); response.end() }
	} catch (error) { response.writeHead(500); response.end(String(error)) }
}).listen(4179, '127.0.0.1', () => console.log('Isolated ClassFlow demo: http://127.0.0.1:4179'))
