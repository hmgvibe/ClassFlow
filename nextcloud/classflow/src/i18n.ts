import { getLanguage, register, translate } from '@nextcloud/l10n'
import { simplified } from './translations.ts'

/**
 * Unknown languages keep ClassFlow's existing traditional Chinese fallback.
 *
 * @param language Nextcloud language code, with either hyphens or underscores.
 */
export function chineseLocale(language: string): 'zh-CN' | 'zh-TW' {
	const normalized = language.replaceAll('_', '-').toLowerCase()
	return /^zh(?:-hans(?:-|$)|-cn(?:-|$)|-sg(?:-|$)|$)/.test(normalized) ? 'zh-CN' : 'zh-TW'
}

const language = typeof window === 'undefined' ? 'zh-TW' : getLanguage()
export const uiLocale = chineseLocale(language)
register('classflow', uiLocale === 'zh-CN' ? simplified : {})

export function t(text: string, placeholders: Record<string, string | number> = {}): string {
	// Vue escapes text and attribute values; do not HTML-escape them twice.
	return translate('classflow', text, placeholders, { escape: false, sanitize: false })
}
