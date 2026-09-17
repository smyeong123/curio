import { ref } from 'vue'
import { useDisclosureSet } from '@/composables/useDisclosureSet'
import type { TopicL1, TopicL2 } from '@/data/topics'

/**
 * State of a beat (topic) picker, shared by onboarding and Settings: the leaf
 * topics chosen so far, which L1 domains are unfolded, and the selection counts
 * the accordion headers show. Topics are canonical names (data/topics.ts).
 */
export function useTopicSelection(initial: string[] = []) {
  const selectedTopics = ref<string[]>([...initial])
  const { openKeys: expandedDomains, toggle: toggleDomain } = useDisclosureSet()

  const toggleTopic = (topic: string) => {
    const index = selectedTopics.value.indexOf(topic)
    if (index === -1) selectedTopics.value.push(topic)
    else selectedTopics.value.splice(index, 1)
  }

  const subcategoryCount = (sub: TopicL2) =>
    sub.topics.filter((topic) => selectedTopics.value.includes(topic)).length

  const domainCount = (domain: TopicL1) =>
    domain.subcategories.reduce((acc, sub) => acc + subcategoryCount(sub), 0)

  return { selectedTopics, toggleTopic, expandedDomains, toggleDomain, domainCount, subcategoryCount }
}
