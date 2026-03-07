/** Vue injection key indicating whether expanded content is rendered inside an embedded context (e.g. card grid). */
import type { InjectionKey } from 'vue'

/** When injected as `true`, signals that the expanded content is embedded within a card grid rather than a table row. */
export const EXPANDED_CONTENT_EMBEDDED_KEY: InjectionKey<boolean> = Symbol('expandedContentEmbedded')
