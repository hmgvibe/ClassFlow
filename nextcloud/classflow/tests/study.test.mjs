import { strict as assert } from 'node:assert'
import { test } from 'node:test'
import { localDay, shiftDay, studyPeriod, studyReference } from '../src/study.ts'

const timestamp = (clock) => new Date(`2026-10-08T${clock}:00`).getTime()
const plan = { id: 'plan', title: '複習', startsAt: timestamp('14:00'), endsAt: timestamp('15:00'), linkedCourseId: null, linkedAgendaId: null, notes: '', version: 0, updatedAt: 0 }
const state = { courses: [], slots: [], agendaItems: [], studyPlans: [], serverTime: 0 }

test('12:00 and 18:00 start new periods', () => {
	assert.equal(studyPeriod(timestamp('11:59')), 0)
	assert.equal(studyPeriod(timestamp('12:00')), 1)
	assert.equal(studyPeriod(timestamp('17:59')), 1)
	assert.equal(studyPeriod(timestamp('18:00')), 2)
})

test('five-day paging crosses month and year boundaries', () => {
	assert.equal(shiftDay('2026-12-29', 5), '2027-01-03')
	assert.equal(shiftDay('2026-10-02', -5), '2026-09-27')
	assert.equal(localDay(timestamp('00:00')), '2026-10-08')
})

test('reference times never change manual study time', () => {
	const linked = { ...plan, linkedCourseId: 'course' }
	const courses = [{ id: 'course', name: '數學', teacher: '', room: '', colorKey: 0, notes: '', version: 1, updatedAt: 0 }]
	const slots = [{ id: 'slot', courseId: 'course', dayOfWeek: 1, startMinutes: 480, endMinutes: 540, roomOverride: '', version: 1, updatedAt: 0 }]
	assert.equal(studyReference(linked, { ...state, courses, slots }), '週一 08:00–09:00')
	assert.equal(linked.startsAt, plan.startsAt)
	assert.equal(linked.endsAt, plan.endsAt)
})

test('missing references show an actionable hint', () => {
	assert.match(studyReference({ ...plan, linkedAgendaId: 'deleted' }, state), /原日程已刪除/)
})
