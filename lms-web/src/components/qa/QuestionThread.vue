<script setup>
import { ref } from 'vue'

const props = defineProps({
  question: { type: Object, required: true },
  canAnswer: { type: Boolean, default: false },
  highlighted: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['answer'])
const answerOpen = ref(false)
const answerText = ref('')

const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : ''

const submit = () => {
  const content = answerText.value.trim()
  if (!content) return
  emit('answer', { questionId: props.question.id, content, done: () => {
    answerText.value = ''
    answerOpen.value = false
  } })
}
</script>

<template>
  <article
    :id="`question-${question.id}`"
    class="question-thread"
    :class="{ highlighted }"
  >
    <header class="question-head">
      <div>
        <h2>{{ question.title }}</h2>
        <p>学生 #{{ question.userId }} · {{ formatTime(question.createTime) }}</p>
      </div>
      <span class="status" :class="question.answers?.length ? 'answered' : 'pending'">
        {{ question.answers?.length ? `已有 ${question.answers.length} 条回答` : '等待回答' }}
      </span>
    </header>

    <p v-if="question.content" class="question-content">{{ question.content }}</p>

    <section v-if="question.answers?.length" class="answers" aria-label="历史回答">
      <article v-for="answer in question.answers" :key="answer.id" class="answer">
        <div class="answer-meta">
          <strong>教师 #{{ answer.userId }}</strong>
          <span>{{ formatTime(answer.createTime) }}</span>
          <span v-if="answer.accepted === 1" class="accepted">已采纳</span>
        </div>
        <p>{{ answer.content }}</p>
      </article>
    </section>

    <footer v-if="canAnswer" class="answer-actions">
      <button v-if="!answerOpen" v-btn-fx class="btn" type="button" @click="answerOpen = true">
        {{ question.answers?.length ? '补充回答' : '回答问题' }}
      </button>
      <div v-else class="answer-editor">
        <label :for="`answer-${question.id}`">写下你的回答</label>
        <textarea
          :id="`answer-${question.id}`"
          v-model="answerText"
          rows="4"
          maxlength="1000"
          placeholder="给出清晰、具体的解答，学生会在消息通知中收到提醒。"
        ></textarea>
        <div class="editor-foot">
          <span>{{ answerText.length }}/1000</span>
          <button v-btn-fx class="btn" type="button" :disabled="submitting" @click="answerOpen = false">取消</button>
          <button v-btn-fx class="btn btn-primary" type="button" :disabled="submitting || !answerText.trim()" @click="submit">
            {{ submitting ? '提交中…' : '发布回答' }}
          </button>
        </div>
      </div>
    </footer>
  </article>
</template>

<style scoped>
.question-thread {
  padding: 22px 24px;
  border: 1px solid #e8edf3;
  border-radius: 8px;
  background: #fff;
  scroll-margin-top: 76px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.question-thread.highlighted {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}

.question-head,
.answer-meta,
.editor-foot {
  display: flex;
  align-items: center;
}

.question-head { justify-content: space-between; gap: 20px; }
.question-head h2 { margin: 0 0 6px; font-size: 18px; line-height: 1.45; color: #263445; }
.question-head p, .answer-meta { color: #8492a6; font-size: 13px; }

.status {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
}
.status.pending { background: #fff7e8; color: #b26a00; }
.status.answered { background: #edf8f2; color: #277a50; }

.question-content { margin-top: 16px; color: #4e5d6c; line-height: 1.75; white-space: pre-wrap; }
.answers { margin-top: 20px; border-left: 3px solid #dcecff; }
.answer { padding: 14px 18px; background: #f8fbff; border-bottom: 1px solid #e7f0fa; }
.answer:last-child { border-bottom: 0; }
.answer-meta { gap: 10px; margin-bottom: 8px; }
.answer-meta strong { color: #2f6fa9; }
.answer p { color: #34495e; line-height: 1.7; white-space: pre-wrap; }
.accepted { color: #2f8a5b; }

.answer-actions { margin-top: 18px; }
.answer-editor label { display: block; margin-bottom: 8px; color: #526477; font-size: 14px; font-weight: 600; }
.answer-editor textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #cfd9e5;
  border-radius: 6px;
  resize: vertical;
  font: inherit;
  line-height: 1.6;
}
.answer-editor textarea:focus { outline: 2px solid rgba(64, 158, 255, 0.2); border-color: #409eff; }
.editor-foot { justify-content: flex-end; gap: 10px; margin-top: 10px; }
.editor-foot > span { margin-right: auto; color: #9aa8b5; font-size: 12px; }

@media (max-width: 640px) {
  .question-thread { padding: 18px 16px; }
  .question-head { align-items: flex-start; flex-direction: column; gap: 10px; }
}
</style>
