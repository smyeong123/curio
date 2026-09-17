import { ref } from 'vue'

/**
 * Which keys of a keyed disclosure list (accordion domains, filter groups)
 * are unfolded. Any number can be open at once; `toggle` folds an open key
 * and unfolds a closed one. `openKeys` is the underlying set for templates
 * that read it directly.
 */
export function useDisclosureSet() {
  const openKeys = ref<Set<string>>(new Set())

  const isOpen = (key: string) => openKeys.value.has(key)

  const toggle = (key: string) => {
    if (openKeys.value.has(key)) openKeys.value.delete(key)
    else openKeys.value.add(key)
  }

  return { openKeys, isOpen, toggle }
}
