<template>
  <svg
    :viewBox="icon.viewBox"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :stroke="icon.stroke === false ? undefined : 'currentColor'"
    :stroke-width="iconStrokeWidth"
    aria-hidden="true"
  >
    <path
      v-for="(path, index) in icon.paths"
      :key="`${iconName}-${index}`"
      :d="path.d"
      :fill="path.fill"
      :fill-rule="path.fillRule"
      :clip-rule="path.clipRule"
      :stroke-linecap="path.strokeLinecap ?? 'round'"
      :stroke-linejoin="path.strokeLinejoin ?? 'round'"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

// Every name here is rendered somewhere: ErrorBoundary/BaseInput (home, warning),
// the topic hierarchy's L1 domains (TopicIcon), the Archive beat filter (archive)
// and the onboarding presets (globe).
type IconName =
  | 'archive'
  | 'home'
  | 'warning'
  | 'brain'
  | 'bot'
  | 'rocket'
  | 'compass'
  | 'globe'

type IconPath = {
  d: string
  fill?: string
  fillRule?: 'evenodd' | 'nonzero'
  clipRule?: 'evenodd' | 'nonzero'
  strokeLinecap?: 'round' | 'butt' | 'square'
  strokeLinejoin?: 'round' | 'miter' | 'bevel'
}

type IconDefinition = {
  viewBox: string
  stroke?: boolean
  paths: IconPath[]
}

const icons: Record<IconName, IconDefinition> = {
  archive: {
    viewBox: '0 0 24 24',
    paths: [{ d: 'M20.25 6.375v11.25A2.25 2.25 0 0 1 18 19.875H6A2.25 2.25 0 0 1 3.75 17.625V6.375m16.5 0H3.75m16.5 0L18.75 4.5H5.25L3.75 6.375m4.5 4.125h7.5' }]
  },
  home: {
    viewBox: '0 0 24 24',
    paths: [{ d: 'm2.25 12 8.954-8.955a1.125 1.125 0 0 1 1.592 0L21.75 12M4.5 9.75v9a1.5 1.5 0 0 0 1.5 1.5h3.75v-5.25A1.5 1.5 0 0 1 11.25 13.5h1.5a1.5 1.5 0 0 1 1.5 1.5v5.25H18a1.5 1.5 0 0 0 1.5-1.5v-9' }]
  },
  warning: {
    viewBox: '0 0 24 24',
    paths: [{ d: 'M12 9v3.75m0 3.75h.008v.008H12v-.008Zm9.74 2.26-8.999-15.588a.9.9 0 0 0-1.558 0L2.184 18.76a.9.9 0 0 0 .779 1.35h17.999a.9.9 0 0 0 .778-1.35Z' }]
  },
  brain: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z' },
      { d: 'M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z' },
      { d: 'M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4' }
    ]
  },
  bot: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M12 8V4H8' },
      { d: 'M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z' },
      { d: 'M2 14h2m16 0h2M15 13v2M9 13v2' }
    ]
  },
  rocket: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z' },
      { d: 'm12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z' },
      { d: 'M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0m1 7v5s3.03-.55 4-2c1.08-1.62 0-5 0-5' }
    ]
  },
  compass: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z' },
      { d: 'm16.24 7.76-2.12 6.36-6.36 2.12 2.12-6.36 6.36-2.12Z' }
    ]
  },
  globe: {
    viewBox: '0 0 24 24',
    paths: [
      { d: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z' },
      { d: 'M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20M2 12h20' }
    ]
  }

}

const props = withDefaults(defineProps<{
  name: IconName
  strokeWidth?: number | string
}>(), {
  strokeWidth: 1.9
})

const icon = computed(() => icons[props.name])
const iconName = computed(() => props.name)
const iconStrokeWidth = computed(() => props.strokeWidth)
</script>
