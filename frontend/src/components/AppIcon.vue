<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: {
    type: String,
    required: true
  },
  size: {
    type: [Number, String],
    default: 18
  }
})

const icons = {
  chat: ['M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z'],
  library: [
    'M4 19.5A2.5 2.5 0 0 1 6.5 17H20',
    'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'
  ],
  review: [
    'M3 12a9 9 0 1 0 3-6.7L3 8',
    'M3 3v5h5',
    'M12 7v5l3 2'
  ],
  heart: ['M20.6 13.4 12 22l-8.6-8.6a4 4 0 0 1 5.6-5.6l3 3 3-3a4 4 0 0 1 5.6 5.6z'],
  file: [
    'M4 4h16v16H4z',
    'M8 9h8',
    'M8 13h6'
  ],
  settings: [
    'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
    'M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6 1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1z'
  ],
  search: [
    'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z',
    'm20 20-4-4'
  ],
  filter: [
    'M4 6h16',
    'M7 12h10',
    'M10 18h4'
  ],
  plus: ['M12 5v14', 'M5 12h14'],
  logout: [
    'M10 17l5-5-5-5',
    'M15 12H3',
    'M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4'
  ],
  'arrow-left': ['m15 18-6-6 6-6', 'M9 12h12'],
  edit: [
    'M12 20h9',
    'M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z'
  ],
  trash: [
    'M3 6h18',
    'M8 6V4h8v2',
    'M19 6l-1 15H6L5 6',
    'M10 11v6',
    'M14 11v6'
  ],
  send: ['m6 12 6-6 6 6', 'M12 18V6'],
  'chevron-down': ['m6 9 6 6 6-6'],
  sparkles: ['M12 3 9.8 9.8 3 12l6.8 2.2L12 21l2.2-6.8L21 12l-6.8-2.2z'],
  check: ['m5 12 4 4L19 6']
}

const paths = computed(() => icons[props.name] || [])
</script>

<template>
  <svg
      class="app-icon"
      :width="size"
      :height="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
  >
    <path v-for="path in paths" :key="path" :d="path" />
  </svg>
</template>
