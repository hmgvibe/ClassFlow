<script setup lang="ts">
import type { AgendaItem, AgendaType, ClassFlowState, Course, Mutation, TimetableSlot } from './types.ts'

import { computed, onMounted, reactive, ref } from 'vue'
import NcButton from '@nextcloud/vue/components/NcButton'
import NcDialog from '@nextcloud/vue/components/NcDialog'
import NcLoadingIcon from '@nextcloud/vue/components/NcLoadingIcon'
import NcTextField from '@nextcloud/vue/components/NcTextField'
import { applyMutation, loadState } from './api.ts'

const emptyState = (): ClassFlowState => ({ courses: [], slots: [], agendaItems: [], serverTime: 0 })
const state = ref<ClassFlowState>(emptyState())
const activeTab = ref<'timetable' | 'agenda'>('timetable')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const selectedDay = ref(Math.min(new Date().getDay() || 7, 7))
const query = ref('')
const typeFilter = ref<AgendaType | 'all'>('all')
const showCompleted = ref(false)
const agendaLinkDay = ref(1)

const dialog = ref<'course' | 'slot' | 'agenda' | null>(null)
const courseForm = reactive<Course>({ id: '', name: '', teacher: '', room: '', colorKey: 0, notes: '', version: 0, updatedAt: 0 })
const slotForm = reactive<TimetableSlot>({ id: '', courseId: '', dayOfWeek: 1, startMinutes: 480, endMinutes: 540, roomOverride: '', version: 0, updatedAt: 0 })
const agendaForm = reactive<AgendaItem>({ id: '', type: 'homework', title: '', occursAt: Date.now() + 86400000, endsAt: null, allDay: false, status: 'pending', notes: '', reminderAt: null, linkedSlotIds: [], version: 0, updatedAt: 0 })

const days = ['週一', '週二', '週三', '週四', '週五', '週六', '週日']
const colors = [
	'#00796b',
	'#1565c0',
	'#7b1fa2',
	'#d05a00',
	'#2e7d32',
	'#c2185b',
	'#00838f',
	'#c62828',
	'#3949ab',
	'#a66a00',
	'#6b7d12',
	'#6d4c41',
	'#5e35b1',
	'#ad1457',
	'#0277bd',
	'#558b2f',
	'#ef6c00',
	'#00897b',
	'#9e4a32',
	'#546e7a',
	'#6a1b9a',
	'#00695c',
	'#283593',
	'#b71c1c',
]
const maxColorKey = 0x7FFF
const typeLabels: Record<AgendaType, string> = { homework: '作業', exam: '考試', activity: '活動', other: '其他' }

const displayCourses = computed(() => distinctCourseColors(state.value.courses))
const courseMap = computed(() => new Map(displayCourses.value.map((course) => [course.id, course])))
const agendaLinkSlots = computed(() => state.value.slots
	.filter((slot) => slot.dayOfWeek === agendaLinkDay.value)
	.sort((left, right) => left.startMinutes - right.startMinutes))
const filteredAgenda = computed(() => state.value.agendaItems
	.filter((item) => showCompleted.value || item.status === 'pending')
	.filter((item) => typeFilter.value === 'all' || item.type === typeFilter.value)
	.filter((item) => !query.value || `${item.title} ${item.notes}`.toLowerCase().includes(query.value.toLowerCase()))
	.sort((a, b) => a.occursAt - b.occursAt))

const agendaGroups = computed(() => {
	const groups = new Map<string, AgendaItem[]>()
	for (const item of filteredAgenda.value) {
		const key = new Date(item.occursAt).toLocaleDateString('sv-SE')
		groups.set(key, [...(groups.get(key) ?? []), item])
	}
	return [...groups.entries()]
})

onMounted(refresh)

async function refresh() {
	loading.value = true
	error.value = ''
	try {
		state.value = await loadState()
	} catch (reason) {
		error.value = message(reason)
	} finally {
		loading.value = false
	}
}

async function mutate(entityType: Mutation['entityType'], operation: Mutation['operation'], payload: Course | TimetableSlot | AgendaItem) {
	saving.value = true
	error.value = ''
	try {
		state.value = await applyMutation({
			operationId: crypto.randomUUID(),
			entityType,
			entityId: payload.id,
			operation,
			baseVersion: payload.version,
			payload: operation === 'delete' ? null : payload,
		})
		dialog.value = null
	} catch (reason) {
		error.value = message(reason)
	} finally {
		saving.value = false
	}
}

function editCourse(course?: Course) {
	Object.assign(courseForm, course ?? { id: crypto.randomUUID(), name: '', teacher: '', room: '', colorKey: randomCourseColorKey(), notes: '', version: 0, updatedAt: 0 })
	dialog.value = 'course'
}

function courseColor(colorKey: number) {
	const rgb = courseColorValue(colorKey)
	return `#${rgb.toString(16).padStart(6, '0')}`
}

function courseColorValue(colorKey: number) {
	if (colorKey >= 0 && colorKey < colors.length) {
		return Number.parseInt(colors[colorKey].slice(1), 16)
	}

	const packed = Math.trunc(colorKey) & maxColorKey
	const red = Math.round(((packed >> 10) & 0x1F) * 255 / 31)
	const green = Math.round(((packed >> 5) & 0x1F) * 255 / 31)
	const blue = Math.round((packed & 0x1F) * 255 / 31)
	return (red << 16) | (green << 8) | blue
}

function distinctCourseColors(courses: Course[]) {
	const usedColors = new Set<number>()
	const resolvedKeys = new Map<string, number>()

	for (const course of [...courses].sort((left, right) => left.id.localeCompare(right.id))) {
		const preferredColor = courseColorValue(course.colorKey)
		if (!usedColors.has(preferredColor)) {
			usedColors.add(preferredColor)
			resolvedKeys.set(course.id, course.colorKey)
			continue
		}

		const resolvedKey = deterministicUnusedColorKey(course.id, usedColors)
		usedColors.add(resolvedKey)
		resolvedKeys.set(course.id, resolvedKey)
	}

	return courses.map((course) => {
		const colorKey = resolvedKeys.get(course.id) ?? course.colorKey
		return colorKey === course.colorKey ? course : { ...course, colorKey }
	})
}

function deterministicUnusedColorKey(courseId: string, usedColors: Set<number>) {
	const startIndex = modulo(javaStringHash(courseId), colors.length)
	for (let offset = 0; offset < colors.length; offset++) {
		const candidateKey = (startIndex + offset) % colors.length
		if (!usedColors.has(courseColorValue(candidateKey))) {
			return candidateKey
		}
	}

	let seed = javaStringHash(courseId)
	while (true) {
		seed = (Math.imul(seed, 1664525) + 1013904223) | 0
		const candidateKey = colors.length + modulo(seed, maxColorKey - colors.length + 1)
		if (!usedColors.has(courseColorValue(candidateKey))) {
			return candidateKey
		}
	}
}

function javaStringHash(value: string) {
	let hash = 0
	for (let index = 0; index < value.length; index++) {
		hash = (Math.imul(hash, 31) + value.charCodeAt(index)) | 0
	}
	return hash
}

function modulo(value: number, divisor: number) {
	return ((value % divisor) + divisor) % divisor
}

function randomCourseColorKey() {
	const usedColors = new Set(displayCourses.value.map((course) => courseColorValue(course.colorKey)))
	const availableKeys = colors.map((_, index) => index).filter((key) => !usedColors.has(courseColorValue(key)))
	if (availableKeys.length) {
		return availableKeys[randomIndex(availableKeys.length)]
	}

	for (let attempt = 0; attempt < 720; attempt++) {
		const candidateKey = rgb555Key(hslToRgb(randomIndex(360), 68, 43))
		if (candidateKey >= colors.length && !usedColors.has(courseColorValue(candidateKey))) {
			return candidateKey
		}
	}

	let candidateKey = colors.length
	while (usedColors.has(courseColorValue(candidateKey))) {
		candidateKey++
	}
	return candidateKey
}

function randomIndex(length: number) {
	const value = new Uint32Array(1)
	crypto.getRandomValues(value)
	return value[0] % length
}

function hslToRgb(hue: number, saturation: number, lightness: number) {
	const s = saturation / 100
	const l = lightness / 100
	const chroma = (1 - Math.abs(2 * l - 1)) * s
	const section = hue / 60
	const x = chroma * (1 - Math.abs((section % 2) - 1))
	const [red, green, blue] = section < 1
		? [chroma, x, 0]
		: section < 2
			? [x, chroma, 0]
			: section < 3
				? [0, chroma, x]
				: section < 4
					? [0, x, chroma]
					: section < 5
						? [x, 0, chroma]
						: [chroma, 0, x]
	const match = l - chroma / 2
	return (Math.round((red + match) * 255) << 16)
		| (Math.round((green + match) * 255) << 8)
		| Math.round((blue + match) * 255)
}

function rgb555Key(rgb: number) {
	const red = ((rgb >> 16) & 0xFF) * 31 / 255
	const green = ((rgb >> 8) & 0xFF) * 31 / 255
	const blue = (rgb & 0xFF) * 31 / 255
	return (Math.floor(red) << 10) | (Math.floor(green) << 5) | Math.floor(blue)
}

function editSlot(slot?: TimetableSlot) {
	if (!state.value.courses.length) {
		return editCourse()
	}
	Object.assign(slotForm, slot ?? { id: crypto.randomUUID(), courseId: state.value.courses[0].id, dayOfWeek: selectedDay.value, startMinutes: 480, endMinutes: 540, roomOverride: '', version: 0, updatedAt: 0 })
	dialog.value = 'slot'
}

function editAgenda(item?: AgendaItem) {
	Object.assign(agendaForm, item ? { ...item, linkedSlotIds: [...item.linkedSlotIds] } : { id: crypto.randomUUID(), type: 'homework', title: '', occursAt: Date.now() + 86400000, endsAt: null, allDay: false, status: 'pending', notes: '', reminderAt: null, linkedSlotIds: [], version: 0, updatedAt: 0 })
	const linkedSlot = state.value.slots
		.slice()
		.sort((left, right) => left.dayOfWeek - right.dayOfWeek || left.startMinutes - right.startMinutes)
		.find((slot) => agendaForm.linkedSlotIds.includes(slot.id))
	agendaLinkDay.value = linkedSlot?.dayOfWeek ?? (new Date(agendaForm.occursAt).getDay() || 7)
	agendaForm.linkedSlotIds = linkedSlot ? [linkedSlot.id] : []
	dialog.value = 'agenda'
}

function time(minutes: number) {
	return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
}

function setTime(target: TimetableSlot, key: 'startMinutes' | 'endMinutes', value: string) {
	const [hours, minutes] = value.split(':').map(Number)
	if (Number.isFinite(hours) && Number.isFinite(minutes)) {
		target[key] = hours * 60 + minutes
	}
}

function agendaInputDate(item: AgendaItem) {
	const date = new Date(item.occursAt)
	return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

function setAgendaDate(value: string) {
	const timestamp = new Date(value).getTime()
	if (Number.isFinite(timestamp)) {
		agendaForm.occursAt = timestamp
	}
}

function setAgendaLinkDay(day: number) {
	agendaLinkDay.value = day
	if (!state.value.slots.some((slot) => slot.dayOfWeek === day && agendaForm.linkedSlotIds.includes(slot.id))) {
		agendaForm.linkedSlotIds = []
	}
}

function selectAgendaSlot(id: string) {
	agendaForm.linkedSlotIds = id ? [id] : []
	const slot = state.value.slots.find((value) => value.id === id)
	if (!slot) {
		return
	}
	const date = new Date(agendaForm.occursAt)
	date.setHours(Math.floor(slot.startMinutes / 60), slot.startMinutes % 60, 0, 0)
	agendaForm.occursAt = date.getTime()
	agendaForm.allDay = false
}

function dateHeading(value: string) {
	const date = new Date(`${value}T00:00:00`)
	const today = new Date()
	const tomorrow = new Date()
	tomorrow.setDate(today.getDate() + 1)
	const same = (a: Date, b: Date) => a.toDateString() === b.toDateString()
	const prefix = same(date, today) ? '今天' : same(date, tomorrow) ? '明天' : date.toLocaleDateString('zh-TW', { weekday: 'long' })
	return `${prefix} · ${date.toLocaleDateString('zh-TW', { month: 'numeric', day: 'numeric' })}`
}

function message(reason: unknown) {
	return reason instanceof Error ? reason.message : '操作失敗，請稍後再試。'
}
</script>

<template>
	<main class="classflow-shell">
		<header class="classflow-header">
			<div>
				<p class="eyebrow">
					CLASSFLOW
				</p>
				<h1>{{ activeTab === 'timetable' ? '每週課表' : '日程管理' }}</h1>
				<p class="subtitle">
					{{ activeTab === 'timetable' ? `${state.courses.length} 門課程 · ${state.slots.length} 個時段` : `${filteredAgenda.length} 個顯示中的日程` }}
				</p>
			</div>
			<NcButton variant="tertiary" :disabled="loading" @click="refresh">
				重新整理
			</NcButton>
		</header>

		<nav class="tab-bar" aria-label="主要頁面">
			<button :class="{ active: activeTab === 'timetable' }" @click="activeTab = 'timetable'">
				課表
			</button>
			<button :class="{ active: activeTab === 'agenda' }" @click="activeTab = 'agenda'">
				日程
			</button>
		</nav>

		<div v-if="error" class="notice error" role="alert">
			<span>{{ error }}</span>
			<button aria-label="關閉" @click="error = ''">
				×
			</button>
		</div>

		<div v-if="loading" class="center-state">
			<NcLoadingIcon :size="36" />
			<p>正在載入 ClassFlow…</p>
		</div>

		<template v-else-if="activeTab === 'timetable'">
			<section class="toolbar">
				<div class="day-selector">
					<button
						v-for="(day, index) in days"
						:key="day"
						:class="{ selected: selectedDay === index + 1 }"
						@click="selectedDay = index + 1">
						{{ day }}
					</button>
				</div>
				<div class="toolbar-actions">
					<NcButton variant="secondary" @click="editCourse()">
						新增課程
					</NcButton>
					<NcButton variant="primary" :disabled="!state.courses.length" @click="editSlot()">
						新增課堂
					</NcButton>
				</div>
			</section>

			<section v-if="!state.courses.length" class="center-state empty">
				<div class="empty-icon">
					CF
				</div>
				<h2>先建立第一門課程</h2>
				<p>加入課程名稱、教師與教室，再安排到每週課表。</p>
				<NcButton variant="primary" @click="editCourse()">
					建立課程
				</NcButton>
			</section>

			<section v-else class="timetable-layout">
				<aside class="course-rail">
					<div class="section-label">
						課程
					</div>
					<button
						v-for="course in displayCourses"
						:key="course.id"
						class="course-row"
						@click="editCourse(course)">
						<span class="color-dot" :style="{ background: courseColor(course.colorKey) }" />
						<span><strong>{{ course.name }}</strong><small>{{ [course.teacher, course.room].filter(Boolean).join(' · ') || '尚無詳細資料' }}</small></span>
					</button>
				</aside>
				<div class="day-column">
					<div class="section-label">
						{{ days[selectedDay - 1] }}課堂
					</div>
					<button
						v-for="slot in state.slots.filter(value => value.dayOfWeek === selectedDay).sort((a, b) => a.startMinutes - b.startMinutes)"
						:key="slot.id"
						class="slot-row"
						@click="editSlot(slot)">
						<span class="slot-time"><strong>{{ time(slot.startMinutes) }}</strong><small>{{ time(slot.endMinutes) }}</small></span>
						<span class="slot-accent" :style="{ background: courseColor(courseMap.get(slot.courseId)?.colorKey ?? 0) }" />
						<span class="slot-copy"><strong>{{ courseMap.get(slot.courseId)?.name }}</strong><small>{{ slot.roomOverride || courseMap.get(slot.courseId)?.room || '未指定教室' }}</small></span>
					</button>
					<div v-if="!state.slots.some(value => value.dayOfWeek === selectedDay)" class="inline-empty">
						這天還沒有課堂。
					</div>
				</div>
			</section>
		</template>

		<template v-else>
			<section class="toolbar agenda-tools">
				<NcTextField v-model="query" label="搜尋標題或備註" trailingButtonIcon="close" />
				<select v-model="typeFilter" aria-label="日程類型">
					<option value="all">
						全部類型
					</option>
					<option v-for="(label, key) in typeLabels" :key="key" :value="key">
						{{ label }}
					</option>
				</select>
				<label class="check"><input v-model="showCompleted" type="checkbox"> 顯示已完成</label>
				<NcButton variant="primary" @click="editAgenda()">
					新增日程
				</NcButton>
			</section>

			<section v-if="!agendaGroups.length" class="center-state empty">
				<div class="empty-icon">
					✓
				</div>
				<h2>目前沒有符合條件的日程</h2>
				<p>建立作業、考試或活動，也可以連結到課堂。</p>
				<NcButton variant="primary" @click="editAgenda()">
					新增日程
				</NcButton>
			</section>
			<section v-else class="agenda-list">
				<div v-for="[date, items] in agendaGroups" :key="date" class="agenda-group">
					<h2>{{ dateHeading(date) }}</h2>
					<button
						v-for="item in items"
						:key="item.id"
						class="agenda-row"
						@click="editAgenda(item)">
						<input
							type="checkbox"
							:checked="item.status === 'completed'"
							aria-label="完成狀態"
							@click.stop
							@change="mutate('agenda', 'upsert', { ...item, status: item.status === 'completed' ? 'pending' : 'completed' })">
						<span class="type-dot" :class="item.type" />
						<span class="agenda-copy">
							<small>{{ typeLabels[item.type] }}<template v-if="!item.allDay"> · {{ new Date(item.occursAt).toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit' }) }}</template></small>
							<strong :class="{ done: item.status === 'completed' }">{{ item.title }}</strong>
						</span>
					</button>
				</div>
			</section>
		</template>

		<NcDialog
			v-if="dialog === 'course'"
			name="課程"
			:noClose="saving"
			@closing="dialog = null">
			<div class="dialog-form">
				<NcTextField v-model="courseForm.name" label="課程名稱" />
				<NcTextField v-model="courseForm.teacher" label="教師（選填）" />
				<NcTextField v-model="courseForm.room" label="教室（選填）" />
				<NcTextField v-model="courseForm.notes" label="備註（選填）" />
				<div class="automatic-color">
					<span class="color-preview" :style="{ background: courseColor(courseForm.colorKey) }" />
					<span><strong>課程色彩</strong><small>新增時會自動分配未使用的顏色</small></span>
				</div>
			</div>
			<template #actions>
				<NcButton
					v-if="courseForm.version"
					variant="error"
					:disabled="saving"
					@click="mutate('course', 'delete', courseForm)">
					刪除
				</NcButton>
				<NcButton variant="primary" :disabled="saving || !courseForm.name.trim()" @click="mutate('course', 'upsert', courseForm)">
					儲存
				</NcButton>
			</template>
		</NcDialog>

		<NcDialog
			v-if="dialog === 'slot'"
			name="課堂"
			:noClose="saving"
			@closing="dialog = null">
			<div class="dialog-form">
				<label><span class="field-label">課程</span><select v-model="slotForm.courseId"><option v-for="course in state.courses" :key="course.id" :value="course.id">{{ course.name }}</option></select></label>
				<label><span class="field-label">星期</span><select v-model.number="slotForm.dayOfWeek"><option v-for="(day, index) in days" :key="day" :value="index + 1">{{ day }}</option></select></label>
				<div class="two-columns">
					<label><span class="field-label">開始</span><input type="time" :value="time(slotForm.startMinutes)" @input="setTime(slotForm, 'startMinutes', ($event.target as HTMLInputElement).value)"></label>
					<label><span class="field-label">結束</span><input type="time" :value="time(slotForm.endMinutes)" @input="setTime(slotForm, 'endMinutes', ($event.target as HTMLInputElement).value)"></label>
				</div>
				<NcTextField v-model="slotForm.roomOverride" label="教室覆寫（選填）" />
			</div>
			<template #actions>
				<NcButton
					v-if="slotForm.version"
					variant="error"
					:disabled="saving"
					@click="mutate('slot', 'delete', slotForm)">
					刪除
				</NcButton>
				<NcButton variant="primary" :disabled="saving || slotForm.startMinutes >= slotForm.endMinutes" @click="mutate('slot', 'upsert', slotForm)">
					儲存
				</NcButton>
			</template>
		</NcDialog>

		<NcDialog
			v-if="dialog === 'agenda'"
			name="日程"
			:noClose="saving"
			@closing="dialog = null">
			<div class="dialog-form">
				<NcTextField v-model="agendaForm.title" label="標題" />
				<label><span class="field-label">類型</span><select v-model="agendaForm.type"><option v-for="(label, key) in typeLabels" :key="key" :value="key">{{ label }}</option></select></label>
				<label><span class="field-label">日期與時間</span><input type="datetime-local" :value="agendaInputDate(agendaForm)" @input="setAgendaDate(($event.target as HTMLInputElement).value)"></label>
				<label class="check"><input v-model="agendaForm.allDay" type="checkbox"> 全天</label>
				<NcTextField v-model="agendaForm.notes" label="備註（選填）" />
				<div>
					<span class="field-label">連結課堂</span>
					<small class="field-help">選擇後會自動套用該課堂的開始時間</small>
					<div class="link-selectors">
						<label>
							<span class="field-label">星期</span>
							<select :value="agendaLinkDay" :disabled="!state.slots.length" @change="setAgendaLinkDay(Number(($event.target as HTMLSelectElement).value))">
								<option v-for="(day, index) in days" :key="day" :value="index + 1">{{ day }}</option>
							</select>
						</label>
						<label>
							<span class="field-label">課堂</span>
							<select :value="agendaForm.linkedSlotIds[0] ?? ''" :disabled="!agendaLinkSlots.length" @change="selectAgendaSlot(($event.target as HTMLSelectElement).value)">
								<option value="">{{ agendaLinkSlots.length ? '選擇課堂' : '當天沒有課堂' }}</option>
								<option v-for="slot in agendaLinkSlots" :key="slot.id" :value="slot.id">
									{{ courseMap.get(slot.courseId)?.name }} · {{ time(slot.startMinutes) }}
								</option>
							</select>
						</label>
					</div>
				</div>
			</div>
			<template #actions>
				<NcButton
					v-if="agendaForm.version"
					variant="error"
					:disabled="saving"
					@click="mutate('agenda', 'delete', agendaForm)">
					刪除
				</NcButton>
				<NcButton variant="primary" :disabled="saving || !agendaForm.title.trim()" @click="mutate('agenda', 'upsert', agendaForm)">
					儲存
				</NcButton>
			</template>
		</NcDialog>
	</main>
</template>
