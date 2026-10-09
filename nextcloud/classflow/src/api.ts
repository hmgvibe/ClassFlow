import type { ClassFlowState, Mutation } from './types.ts'

import axios from '@nextcloud/axios'
import { generateOcsUrl } from '@nextcloud/router'
import { serverErrorMessage } from './errors.ts'
import { t } from './i18n.ts'

const stateUrl = generateOcsUrl('/apps/classflow/api/v1/state')
const syncUrl = generateOcsUrl('/apps/classflow/api/v1/sync')

function unwrap<T>(response: { data: { ocs?: { data: T } } | T }): T {
	const body = response.data as { ocs?: { data: T } }
	return body.ocs?.data ?? response.data as T
}

export async function loadState(): Promise<ClassFlowState> {
	return unwrap<ClassFlowState>(await axios.get(stateUrl, { params: { format: 'json' } }))
}

export async function applyMutation(mutation: Mutation): Promise<ClassFlowState> {
	const result = unwrap<{
		state: ClassFlowState
		acceptedOperationIds: string[]
		conflicts: Array<{ operationId: string, server: unknown, error?: string }>
	}>(await axios.post(syncUrl, { mutations: [mutation] }, { params: { format: 'json' } }))

	const conflict = result.conflicts.find((item) => item.operationId === mutation.operationId)
	if (conflict) {
		throw new Error(conflict.error ? serverErrorMessage(conflict.error) : t('資料已在另一個裝置修改，請重新載入後再試。'))
	}
	return result.state
}
