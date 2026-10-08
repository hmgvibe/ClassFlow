import type { ClassFlowState, StudyPlan } from './types.ts'

export const studyPeriods = [
	{ label: '上午', range: '00:00–12:00', hour: 9 },
	{ label: '下午', range: '12:00–18:00', hour: 14 },
	{ label: '晚上', range: '18:00–24:00', hour: 19 },
]

export function localDay(timestamp: number) {
	const day = new Date(timestamp)
	return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
}

export function shiftDay(day: string, offset: number) {
	const date = new Date(`${day}T12:00:00`)
	date.setDate(date.getDate() + offset)
	return localDay(date.getTime())
}

export function studyPeriod(timestamp: number) {
	const hour = new Date(timestamp).getHours()
	return hour < 12 ? 0 : hour < 18 ? 1 : 2
}

export function studyClock(timestamp: number) {
	return new Date(timestamp).toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit', hour12: false })
}

export function studyLink(plan: StudyPlan, state: ClassFlowState) {
	if (plan.linkedCourseId) {
		return state.courses.find((course) => course.id === plan.linkedCourseId)?.name ?? '原課程已刪除'
	}
	if (plan.linkedAgendaId) {
		return state.agendaItems.find((item) => item.id === plan.linkedAgendaId)?.title ?? '原日程已刪除'
	}
	return ''
}

export function studyReference(plan: StudyPlan, state: ClassFlowState) {
	if (plan.linkedCourseId) {
		if (!state.courses.some((course) => course.id === plan.linkedCourseId)) {
			return '原課程已刪除，可重新選擇或取消連結。'
		}
		const slots = state.slots.filter((slot) => slot.courseId === plan.linkedCourseId).sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startMinutes - b.startMinutes)
		const clock = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
		return slots.length ? slots.map((slot) => `${['週一', '週二', '週三', '週四', '週五', '週六', '週日'][slot.dayOfWeek - 1]} ${clock(slot.startMinutes)}–${clock(slot.endMinutes)}`).join('\n') : '這門課程尚未安排課堂。'
	}
	if (plan.linkedAgendaId) {
		const item = state.agendaItems.find((value) => value.id === plan.linkedAgendaId)
		if (!item) {
			return '原日程已刪除，可重新選擇或取消連結。'
		}
		return `${item.type === 'exam' ? '考試時間' : '作業日程時間'}：${new Date(item.occursAt).toLocaleDateString('zh-TW')}${item.allDay ? '（全天）' : ` ${studyClock(item.occursAt)}`}`
	}
	return ''
}
