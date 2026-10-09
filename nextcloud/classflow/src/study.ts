import type { ClassFlowState, StudyPlan } from './types.ts'

import { t, uiLocale } from './i18n.ts'

export const studyPeriods = [
	{ label: t('上午'), range: '00:00–12:00', hour: 9 },
	{ label: t('下午'), range: '12:00–18:00', hour: 14 },
	{ label: t('晚上'), range: '18:00–24:00', hour: 19 },
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
	return new Date(timestamp).toLocaleTimeString(uiLocale, { hour: '2-digit', minute: '2-digit', hour12: false })
}

export function studyLink(plan: StudyPlan, state: ClassFlowState) {
	if (plan.linkedCourseId) {
		return state.courses.find((course) => course.id === plan.linkedCourseId)?.name ?? t('原課程已刪除')
	}
	if (plan.linkedAgendaId) {
		return state.agendaItems.find((item) => item.id === plan.linkedAgendaId)?.title ?? t('原日程已刪除')
	}
	return ''
}

export function studyReference(plan: StudyPlan, state: ClassFlowState) {
	if (plan.linkedCourseId) {
		if (!state.courses.some((course) => course.id === plan.linkedCourseId)) {
			return t('原課程已刪除，可重新選擇或取消連結。')
		}
		const slots = state.slots.filter((slot) => slot.courseId === plan.linkedCourseId).sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startMinutes - b.startMinutes)
		const clock = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
		return slots.length ? slots.map((slot) => `${[t('週一'), t('週二'), t('週三'), t('週四'), t('週五'), t('週六'), t('週日')][slot.dayOfWeek - 1]} ${clock(slot.startMinutes)}–${clock(slot.endMinutes)}`).join('\n') : t('這門課程尚未安排課堂。')
	}
	if (plan.linkedAgendaId) {
		const item = state.agendaItems.find((value) => value.id === plan.linkedAgendaId)
		if (!item) {
			return t('原日程已刪除，可重新選擇或取消連結。')
		}
		return `${item.type === 'exam' ? t('考試時間') : t('作業日程時間')}：${new Date(item.occursAt).toLocaleDateString(uiLocale)}${item.allDay ? t('（全天）') : ` ${studyClock(item.occursAt)}`}`
	}
	return ''
}
