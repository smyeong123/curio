export interface TopicL2 {
  id: string
  name: string
  topics: string[]
}

/** AppIcon names used for L1 domains — must exist in components/ui/AppIcon.vue. */
export type TopicIcon = 'brain' | 'bot' | 'rocket' | 'compass'

export interface TopicL1 {
  id: string
  name: string
  icon: TopicIcon
  description: string
  subcategories: TopicL2[]
}

/**
 * Curio is focused on AI *model* news — new features, releases, and emerging
 * agentic systems. Users subscribe at the L3 (leaf) level; the hierarchy is
 * presented as an accordion in onboarding and Settings.
 *
 * Adding a new leaf topic also requires:
 *   - backend/.../TopicConstants.java ALL_TOPICS
 *   - a Flyway migration if legacy preferences should be mapped
 */
export const TOPIC_HIERARCHY: TopicL1[] = [
  {
    id: 'frontier-labs',
    name: 'Frontier Labs',
    icon: 'brain',
    description: 'New features and releases from the major model labs',
    subcategories: [
      {
        id: 'proprietary-frontier',
        name: 'Proprietary Frontier',
        topics: [
          'Claude (Anthropic)',
          'GPT & ChatGPT (OpenAI)',
          'Gemini (Google DeepMind)',
          'Grok (xAI)'
        ]
      },
      {
        id: 'open-weight-leaders',
        name: 'Open-Weight Leaders',
        topics: [
          'Llama (Meta AI)',
          'DeepSeek',
          'Qwen (Alibaba)',
          'Mistral'
        ]
      }
    ]
  },
  {
    id: 'agentic-tools',
    name: 'Agentic & Developer Tools',
    icon: 'bot',
    description: 'Coding agents, browser-use, and agent frameworks',
    subcategories: [
      {
        id: 'coding-agents',
        name: 'Coding Agents',
        topics: [
          'Claude Code & CLI Agents',
          'Cursor, Aider & IDE Agents',
          'Devin & Autonomous Coders'
        ]
      },
      {
        id: 'agent-platforms',
        name: 'Agent Platforms',
        topics: [
          'Browser & Computer-Use Agents',
          'Agent Frameworks & SDKs'
        ]
      }
    ]
  },
  {
    id: 'capabilities-ecosystem',
    name: 'Capabilities & Ecosystem',
    icon: 'rocket',
    description: 'Capability drops, pricing, and the broader market',
    subcategories: [
      {
        id: 'model-capabilities',
        name: 'Model Capabilities',
        topics: [
          'Reasoning & Context',
          'Multimodal (Vision, Audio, Video)'
        ]
      },
      {
        id: 'market',
        name: 'Market',
        topics: [
          'Pricing & Availability',
          'Benchmarks & Evaluations'
        ]
      }
    ]
  },
  {
    id: 'emerging',
    name: 'Emerging',
    icon: 'compass',
    description: 'Brand-new frontier and agentic models as they land',
    subcategories: [
      {
        id: 'discovery',
        name: 'Discovery',
        topics: [
          'New & Emerging Models'
        ]
      }
    ]
  }
]

/** Flat list of all leaf topics. */
export const ALL_TOPICS: string[] = TOPIC_HIERARCHY.flatMap(d =>
  d.subcategories.flatMap(s => s.topics)
)

/** Map from topic name to its L1 domain name (used by ArchiveView's beat filter). */
export const TOPIC_DOMAIN_MAP: Record<string, string> = Object.fromEntries(
  TOPIC_HIERARCHY.flatMap(d =>
    d.subcategories.flatMap(s => s.topics.map(t => [t, d.name]))
  )
)
