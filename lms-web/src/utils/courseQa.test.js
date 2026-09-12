import test from 'node:test'
import assert from 'node:assert/strict'

import { filterCourseQuestions, notificationPresentation, questionCenterTarget } from './courseQa.js'

test('notification target opens the course question center at the exact question', () => {
  assert.deepEqual(questionCenterTarget(8, 19), {
    path: '/courses/8/qa',
    query: { questionId: '19' }
  })
})

test('question filters separate pending and answered history', () => {
  const questions = [
    { id: 1, answers: [] },
    { id: 2, answers: [{ id: 20 }] },
    { id: 3 }
  ]

  assert.deepEqual(filterCourseQuestions(questions, 'pending').map((item) => item.id), [1, 3])
  assert.deepEqual(filterCourseQuestions(questions, 'answered').map((item) => item.id), [2])
  assert.deepEqual(filterCourseQuestions(questions, 'all').map((item) => item.id), [1, 2, 3])
})

test('teacher notification changes from pending to resolved after an answer', () => {
  assert.deepEqual(notificationPresentation({ type: 1 }), {
    label: '待回答',
    tone: 'pending',
    action: '进入回答'
  })
  assert.deepEqual(notificationPresentation({ type: 3 }), {
    label: '已处理',
    tone: 'resolved',
    action: '查看详情'
  })
  assert.deepEqual(notificationPresentation({ type: 2 }), {
    label: '已回答',
    tone: 'answered',
    action: '查看详情'
  })
})
