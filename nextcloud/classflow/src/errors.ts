import { t } from './i18n.ts'

const serverErrors: Record<string, string> = {
	'Course does not exist': '課程已不存在，請重新整理後再試。',
	'Linked slot does not exist': '連結的課堂已不存在，請重新選擇。',
	'Study link does not exist': '連結對象已不存在，請重新選擇。',
	'Invalid timetable range': '課堂的星期或時間範圍不正確。',
	'Invalid agenda date range': '日程的日期或時間範圍不正確。',
	'Invalid study date range': '結束時間必須晚於開始時間。',
	'A study plan can link to only one target': '請只選擇一個連結對象。',
	'Study links must be homework or exam': '學習計劃只能連結作業或考試日程。',
}

/**
 * Translate recognized protocol errors without modifying API responses or arbitrary user text.
 *
 * @param message Original server validation error.
 */
export function serverErrorMessage(message: string): string {
	if (serverErrors[message]) {
		return t(serverErrors[message])
	}
	if (/^Invalid (?:name|title|teacher|room|notes|colorIndex|dayOfWeek|startMinute|endMinute|type|status|occursAt|endsAt|startsAt|reminderAt|id|linkedCourseId|linkedAgendaId|courseId)$/.test(message)) {
		return t('資料欄位不正確，請檢查後再試。')
	}
	return message
}
