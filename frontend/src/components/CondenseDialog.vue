<script setup>
import { ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  open: {
    type: Boolean,
    default: false
  },
  targetTitle: {
    type: String,
    default: ''
  },
  processing: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['cancel', 'confirm'])
const mode = ref('keep')

watch(() => props.open, (open) => {
  if (open) mode.value = 'keep'
})

const cancel = () => {
  if (!props.processing) emit('cancel')
}

const confirm = () => {
  if (!props.processing) emit('confirm', mode.value === 'keep')
}
</script>

<template>
  <Teleport to="body">
    <div
        v-if="open"
        class="condense-overlay"
        role="dialog"
        aria-modal="true"
        aria-labelledby="condense-dialog-title"
        tabindex="-1"
        @click.self="cancel"
        @keydown.esc="cancel"
    >
      <section class="condense-dialog">
        <header class="dialog-head">
          <span class="dialog-icon"><AppIcon name="sparkles" :size="19" /></span>
          <div>
            <h2 id="condense-dialog-title">AI 精简知识卡</h2>
            <p>{{ targetTitle }}</p>
          </div>
        </header>

        <p class="dialog-intro">选择精简结果如何保存。AI 只会修改标题、正文、摘要、分类和标签，不会修改个人备注与评分。</p>

        <div class="mode-options">
          <button
              type="button"
              class="mode-option"
              :class="{ active: mode === 'keep' }"
              @click="mode = 'keep'"
          >
            <span class="radio"></span>
            <span>
              <strong>保留原文</strong>
              <small>精简版放在正文上方，原文继续保留在下方。</small>
            </span>
          </button>
          <button
              type="button"
              class="mode-option"
              :class="{ active: mode === 'replace' }"
              @click="mode = 'replace'"
          >
            <span class="radio"></span>
            <span>
              <strong>替换原文</strong>
              <small>使用精简结果覆盖当前正文，页面会更简洁。</small>
            </span>
          </button>
        </div>

        <p v-if="mode === 'keep'" class="keep-note">
          精简版与原文之间会自动加入分隔标记，重复精简时会更新上方精简版，不会反复叠加。
        </p>

        <footer class="dialog-actions">
          <button class="btn btn-ghost" type="button" :disabled="processing" @click="cancel">取消</button>
          <button class="btn btn-primary" type="button" :disabled="processing" @click="confirm">
            {{ processing ? 'AI 精简中…' : '开始精简' }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.condense-overlay {
  position: fixed;
  inset: 0;
  z-index: 150;
  display: grid;
  place-items: center;
  padding: 22px;
  background: rgba(18, 28, 43, 0.5);
  backdrop-filter: blur(3px);
}

.condense-dialog {
  width: min(520px, 100%);
  padding: 23px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: 0 24px 70px rgba(18, 28, 43, 0.22);
}

.dialog-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.dialog-icon {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.dialog-head h2 {
  font-size: 18px;
  font-weight: 700;
}

.dialog-head p {
  max-width: 390px;
  margin-top: 5px;
  overflow: hidden;
  color: var(--color-muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialog-intro {
  margin: 18px 0 14px;
  color: var(--color-ink-soft);
  font-size: 11px;
  line-height: 1.7;
}

.mode-options {
  display: grid;
  gap: 9px;
}

.mode-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 13px;
  border: 1px solid var(--color-line);
  border-radius: 7px;
  background: #fff;
  color: var(--color-ink-soft);
  cursor: pointer;
  text-align: left;
}

.mode-option:hover {
  border-color: #b9c9e8;
}

.mode-option.active {
  border-color: #a9c0ef;
  background: #f7f9ff;
}

.radio {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
  margin-top: 2px;
  border: 1px solid #b7c2d1;
  border-radius: 50%;
  background: #fff;
}

.mode-option.active .radio {
  border: 4px solid var(--color-primary);
}

.mode-option strong,
.mode-option small {
  display: block;
}

.mode-option strong {
  color: var(--color-ink);
  font-size: 12px;
}

.mode-option small {
  margin-top: 4px;
  color: var(--color-muted);
  font-size: 10px;
  line-height: 1.55;
}

.keep-note {
  margin-top: 10px;
  padding: 9px 11px;
  border-left: 2px solid #95afe4;
  background: #f7f9fd;
  color: #6e7d91;
  font-size: 9px;
  line-height: 1.6;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}

@media (max-width: 520px) {
  .condense-dialog {
    padding: 19px;
  }
}
</style>
