import { strict as assert } from 'node:assert'
import { test } from 'node:test'
import { chineseLocale, t } from '../src/i18n.ts'
import { simplified } from '../src/translations.ts'
import { serverErrorMessage } from '../src/errors.ts'

test('Chinese script and region selection retains the traditional fallback', () => {
	for (const locale of ['zh_CN', 'zh-CN', 'zh-SG', 'zh-Hans', 'zh-Hans-CN', 'zh']) {
		assert.equal(chineseLocale(locale), 'zh-CN')
	}
	for (const locale of ['zh_TW', 'zh-HK', 'zh-Hant', 'en-US', 'ja']) {
		assert.equal(chineseLocale(locale), 'zh-TW')
	}
})

test('every simplified message preserves the original interpolation variables', () => {
	const placeholders = (value) => [...value.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort()
	for (const [source, translation] of Object.entries(simplified)) {
		assert.deepEqual(placeholders(translation), placeholders(source), source)
	}
	assert.equal(simplified['課表'], '课表')
	assert.equal(simplified['學習計劃'], '学习计划')
})

test('fallback interpolation keeps user text including HTML as literal data', () => {
	const userText = '<b>數學 & 复习</b>'
	assert.equal(t('課程 · {arg1}', { arg1: userText }), `課程 · ${userText}`)
})

test('known server validation errors are localized without altering unknown data', () => {
	assert.equal(serverErrorMessage('Invalid study date range'), '結束時間必須晚於開始時間。')
	assert.equal(serverErrorMessage('Invalid title'), '資料欄位不正確，請檢查後再試。')
	assert.equal(serverErrorMessage('user supplied 原文'), 'user supplied 原文')
})
