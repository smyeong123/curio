export interface NewsSummary {
  headline: string
  summary: string
  why_it_matters: string
  source_url: string
  source_name: string
  topic: string
}

export interface DigestContent {
  summaries: NewsSummary[]
  generatedFor: string[]
  /** Edition the stories were written in; absent on digests generated before editions existed (English). */
  language?: 'en' | 'ko'
}

export interface Digest {
  id: string
  content: DigestContent
  generatedAt: string
  emailSentAt: string | null
}

export interface DigestPage {
  content: Digest[]
  totalPages: number
  totalElements: number
  number: number
  size: number
}
