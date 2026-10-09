<script setup lang="ts">
import type { ClassFlowState, StudyPlan } from './types.ts'

import { computed, reactive, ref } from 'vue'
import NcButton from '@nextcloud/vue/components/NcButton'
import NcDialog from '@nextcloud/vue/components/NcDialog'
import NcTextField from '@nextcloud/vue/components/NcTextField'
import { t, uiLocale } from './i18n.ts'
import { localDay, shiftDay, studyClock, studyLink, studyPeriod, studyPeriods, studyReference } from './study.ts'

const props = defineProps<{
	state: ClassFlowState
	saving: boolean
	error: string
	mutate: (operation: 'upsert' | 'delete', plan: StudyPlan) => Promise<boolean>
}>()
const today = () => localDay(Date.now())
const selectedDay = ref(today())
const firstDay = ref(shiftDay(today(), -2))
const dates = computed(() => Array.from({ length: 5 }, (_, i) => shiftDay(firstDay.value, i)))
const plans = computed(() => (props.state.studyPlans ?? []).filter((plan) => localDay(plan.startsAt) === selectedDay.value).sort((a, b) => a.startsAt - b.startsAt))
const editing = ref(false)
const deleting = ref(false)
const existing = ref(false)
const form = reactive<StudyPlan>({ id: '', title: '', startsAt: 0, endsAt: 0, linkedCourseId: null, linkedAgendaId: null, notes: '', version: 0, updatedAt: 0 })
const formDate = ref(today())
const start = ref('14:00')
const end = ref('15:00')
const kind = ref('none')
const linkId = computed({ get: () => form.linkedCourseId ?? form.linkedAgendaId ?? '', set: (id: string) => {
	form.linkedCourseId = kind.value === 'course' ? id || null : null
	form.linkedAgendaId = kind.value !== 'course' ? id || null : null
} })
const options = computed(() => kind.value === 'course'
	? props.state.courses.map((item) => ({ id: item.id, label: item.name }))
	: props.state.agendaItems.filter((item) => item.type === kind.value).sort((a, b) => a.occursAt - b.occursAt).map((item) => ({ id: item.id, label: item.title })))
const hint = computed(() => studyReference(form, props.state))
const startsAt = computed(() => new Date(`${formDate.value}T${start.value}:00`).getTime())
const endsAt = computed(() => new Date(`${formDate.value}T${end.value}:00`).getTime())
const valid = computed(() => form.title.trim().length > 0 && form.title.length <= 255 && form.notes.length <= 10000 && Number.isFinite(startsAt.value) && endsAt.value > startsAt.value)

function selectDate(day: string) {
	selectedDay.value = day
	if (!dates.value.includes(day)) {
		firstDay.value = shiftDay(day, -2)
	}
}

function page(offset: number) {
	firstDay.value = shiftDay(firstDay.value, offset)
	selectedDay.value = shiftDay(selectedDay.value, offset)
}

function edit(plan?: StudyPlan, hour = 14) {
	existing.value = !!plan
	Object.assign(form, plan ?? { id: crypto.randomUUID(), title: '', startsAt: 0, endsAt: 0, linkedCourseId: null, linkedAgendaId: null, notes: '', version: 0, updatedAt: 0 })
	formDate.value = plan ? localDay(plan.startsAt) : selectedDay.value
	start.value = plan ? studyClock(plan.startsAt) : `${String(hour).padStart(2, '0')}:00`
	end.value = plan ? studyClock(plan.endsAt) : `${String(hour + 1).padStart(2, '0')}:00`
	kind.value = form.linkedCourseId ? 'course' : form.linkedAgendaId ? props.state.agendaItems.find((item) => item.id === form.linkedAgendaId)?.type ?? 'homework' : 'none'
	editing.value = true
}

async function save() {
	if (!valid.value) {
		return
	}
	if (await props.mutate('upsert', { ...form, title: form.title.trim(), startsAt: startsAt.value, endsAt: endsAt.value })) {
		selectDate(formDate.value)
		editing.value = false
	}
}

async function remove() {
	if (await props.mutate('delete', { ...form })) {
		deleting.value = false
		editing.value = false
	}
}
</script>

<template>
	<section class="study-page">
		<div class="study-toolbar">
			<label class="study-date-jump"><span class="field-label">{{ t('選擇日期') }}</span><input type="date" :value="selectedDay" @change="selectDate(($event.target as HTMLInputElement).value || today())"></label>
			<div class="toolbar-actions">
				<NcButton variant="tertiary" @click="page(-5)">
					{{ t('前五天') }}
				</NcButton>
				<NcButton variant="tertiary" @click="selectDate(today())">
					{{ t('今天') }}
				</NcButton>
				<NcButton variant="tertiary" @click="page(5)">
					{{ t('後五天') }}
				</NcButton>
			</div>
		</div>
		<nav class="study-dates" :aria-label="t('計劃日期')">
			<button
				v-for="date in dates"
				:key="date"
				:class="{ selected: date === selectedDay }"
				:aria-pressed="date === selectedDay"
				@click="selectDate(date)">
				<small>{{ date === today() ? t("今天") : new Date(`${date}T12:00:00`).toLocaleDateString(uiLocale, { weekday: 'short' }) }}</small>
				<strong>{{ new Date(`${date}T12:00:00`).getDate() }}</strong>
			</button>
		</nav>
		<p class="study-summary">
			{{ t('{arg1} · {arg2} 個計劃', { arg1: selectedDay, arg2: plans.length }) }}
		</p>
		<p v-if="!state.studyPlans" class="notice">
			{{ t('請先更新伺服器上的 ClassFlow App，以使用學習計劃。') }}
		</p>
		<div v-for="(period, index) in studyPeriods" :key="period.label" class="study-section">
			<header class="study-section-heading">
				<h2>{{ period.label }}</h2><small>{{ period.range }}</small>
				<NcButton variant="tertiary" :disabled="saving || !state.studyPlans" @click="edit(undefined, period.hour)">
					{{ t('新增計劃') }}
				</NcButton>
			</header>
			<p v-if="!plans.some(plan => studyPeriod(plan.startsAt) === index)" class="study-empty">
				{{ t('尚未安排') }}
			</p>
			<button
				v-for="plan in plans.filter(item => studyPeriod(item.startsAt) === index)"
				:key="plan.id"
				class="study-row"
				:disabled="saving"
				@click="edit(plan)">
				<span class="study-times"><strong>{{ studyClock(plan.startsAt) }}</strong><small>{{ studyClock(plan.endsAt) }}</small></span>
				<span class="study-copy"><strong>{{ plan.title }}</strong><small v-if="studyLink(plan, state)">{{ studyLink(plan, state) }}</small><small v-if="plan.notes" class="study-notes">{{ plan.notes }}</small></span>
			</button>
		</div>
		<NcDialog
			v-if="editing"
			:name="existing ? t('編輯計劃') : t('新增計劃')"
			:noClose="saving"
			@closing="editing = false">
			<div class="dialog-form study-editor">
				<p v-if="error" class="notice error" role="alert">
					{{ error }}
				</p>
				<NcTextField v-model="form.title" :label="t('計劃標題')" />
				<label><span class="field-label">{{ t('日期') }}</span><input v-model="formDate" type="date"></label>
				<div class="two-columns">
					<label><span class="field-label">{{ t('開始時間') }}</span><input v-model="start" type="time"></label><label><span class="field-label">{{ t('結束時間') }}</span><input v-model="end" type="time"></label>
				</div>
				<small v-if="!Number.isFinite(startsAt) || endsAt <= startsAt" class="study-error">{{ t('結束時間必須晚於開始時間。') }}</small>
				<label><span class="field-label">{{ t('連結對象（選填）') }}</span><select v-model="kind" @change="linkId = ''"><option value="none">{{ t('不連結') }}</option><option value="course">{{ t('課程') }}</option><option value="homework">{{ t('作業') }}</option><option value="exam">{{ t('考試') }}</option></select></label>
				<template v-if="kind !== 'none'">
					<label><span class="field-label">{{ t('選擇項目') }}</span><select v-model="linkId"><option value="">{{ options.length ? t("選擇連結對象") : t("目前沒有可連結的項目") }}</option><option v-if="linkId && !options.some(item => item.id === linkId)" :value="linkId">{{ t('原連結已刪除') }}</option><option v-for="item in options" :key="item.id" :value="item.id">{{ item.label }}</option></select></label>
					<p v-if="hint" class="study-reference">
						{{ hint }}
					</p>
					<small class="field-help">{{ t('以上時間僅供參考，不會自動更改計劃時間。') }}</small>
				</template>
				<label><span class="field-label">{{ t('備註（選填）') }}</span><textarea v-model="form.notes" rows="3" maxlength="10000" /></label>
			</div>
			<template #actions>
				<NcButton
					v-if="existing"
					variant="error"
					:disabled="saving"
					@click="deleting = true">
					{{ t('刪除') }}
				</NcButton>
				<NcButton variant="primary" :disabled="saving || !valid" @click="save">
					{{ saving ? t("儲存中…") : t("儲存") }}
				</NcButton>
			</template>
		</NcDialog>
		<NcDialog
			v-if="deleting"
			:name="t('刪除計劃？')"
			:noClose="saving"
			@closing="deleting = false">
			<p>{{ t('只會刪除「{arg1}」，不會刪除連結的課程或日程。', { arg1: form.title }) }}</p>
			<template #actions>
				<NcButton :disabled="saving" @click="deleting = false">
					{{ t('取消') }}
				</NcButton><NcButton variant="error" :disabled="saving" @click="remove">
					{{ t('刪除') }}
				</NcButton>
			</template>
		</NcDialog>
	</section>
</template>
