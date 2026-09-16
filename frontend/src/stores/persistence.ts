import type { PiniaPluginContext } from 'pinia'

/**
 * Lightweight Pinia persistence plugin. Opt-in via:
 *
 *   defineStore('news', { state: () => ({...}), persist: { paths: ['filter'] } })
 *
 * Only listed paths are mirrored to localStorage so we never accidentally
 * persist JWTs or other sensitive blobs. Failures are swallowed so private-mode
 * users still get a working app.
 */
type PersistOptions = {
  /** Storage key. Defaults to `curio:store:<id>`. */
  key?: string
  /** Top-level state keys to persist. Required — there is no all-paths default. */
  paths: string[]
}

declare module 'pinia' {
  export interface DefineStoreOptionsBase<S, Store> {
    persist?: PersistOptions
  }
}

export function persistencePlugin({ options, store }: PiniaPluginContext) {
  const persist = options.persist
  if (!persist || !persist.paths.length) return

  const key = persist.key ?? `curio:store:${store.$id}`

  try {
    const raw = localStorage.getItem(key)
    if (raw) {
      const restored = JSON.parse(raw)
      const filtered: Record<string, unknown> = {}
      for (const path of persist.paths) {
        if (path in restored) filtered[path] = restored[path]
      }
      // Cast through unknown — Pinia's $patch generic is narrower than our
      // dynamic-paths approach, but the runtime shape is compatible.
      store.$patch(filtered as unknown as Parameters<typeof store.$patch>[0])
    }
  } catch {
    /* ignore — corrupt or unavailable storage */
  }

  store.$subscribe((_mutation, state) => {
    try {
      const snapshot: Record<string, unknown> = {}
      for (const path of persist.paths) {
        snapshot[path] = (state as Record<string, unknown>)[path]
      }
      localStorage.setItem(key, JSON.stringify(snapshot))
    } catch {
      /* ignore */
    }
  })
}
