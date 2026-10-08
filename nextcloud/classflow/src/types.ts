export type AgendaType = 'homework' | 'exam' | 'activity' | 'other'
export type AgendaStatus = 'pending' | 'completed'

export interface Course {
	id: string
	name: string
	teacher: string
	room: string
	colorKey: number
	notes: string
	version: number
	updatedAt: number
}

export interface TimetableSlot {
	id: string
	courseId: string
	dayOfWeek: number
	startMinutes: number
	endMinutes: number
	roomOverride: string
	version: number
	updatedAt: number
}

export interface AgendaItem {
	id: string
	type: AgendaType
	title: string
	occursAt: number
	endsAt: number | null
	allDay: boolean
	status: AgendaStatus
	notes: string
	reminderAt: number | null
	linkedSlotIds: string[]
	version: number
	updatedAt: number
}

export interface ClassFlowState {
	courses: Course[]
	slots: TimetableSlot[]
	agendaItems: AgendaItem[]
	serverTime: number
	studyPlans?: StudyPlan[]
}

export interface StudyPlan {
	id: string
	title: string
	startsAt: number
	endsAt: number
	linkedCourseId: string | null
	linkedAgendaId: string | null
	notes: string
	version: number
	updatedAt: number
}

export interface Mutation {
	operationId: string
	entityType: 'course' | 'slot' | 'agenda' | 'study'
	entityId: string
	operation: 'upsert' | 'delete'
	baseVersion: number
	payload: Course | TimetableSlot | AgendaItem | StudyPlan | null
}
