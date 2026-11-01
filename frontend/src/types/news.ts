export interface NewsSummary {
  headline: string
  summary: string
  why_it_matters: string
  source_url: string
  source_name: string
  topic: string
  /** ISO instant the cited article was published; absent on older digests or undated sources. */
  published_at?: string | null
  /** Other outlets that reported the same story; absent on older digests. */
  also_covered_by?: SourceLink[] | null
  /** 1 (minor update) to 5 (major launch); the digest is ordered by it. Absent on older digests. */
  importance?: number | null
}

export interface SourceLink {
  name: string
  url: string
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
