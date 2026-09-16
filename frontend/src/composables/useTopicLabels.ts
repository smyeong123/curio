import { useI18n } from 'vue-i18n'
import type { TopicL1, TopicL2 } from '@/data/topics'

/**
 * Display labels for the topic taxonomy. The topic NAMES in data/topics.ts are
 * canonical ids — the backend stores them (user_preferences.topics) and digests
 * carry them — so they are never translated at the data layer. This maps a
 * name to the current edition's label at render time; anything unmapped
 * (including every name in the English edition) renders as-is.
 */
export function useTopicLabels() {
  const { locale, t, te, tm, rt } = useI18n()

  const topicLabel = (name: string): string => {
    if (locale.value === 'en') return name
    const leaves = tm('topics.leaves') as Record<string, string> | undefined
    const raw = leaves?.[name]
    return typeof raw === 'string' ? rt(raw) : name
  }

  const groupName = (group: Pick<TopicL1 | TopicL2, 'id' | 'name'>): string => {
    const key = `topics.groups.${group.id}.name`
    return te(key) ? t(key) : group.name
  }

  const groupDescription = (group: Pick<TopicL1, 'id' | 'description'>): string => {
    const key = `topics.groups.${group.id}.description`
    return te(key) ? t(key) : group.description
  }

  return { topicLabel, groupName, groupDescription }
}
