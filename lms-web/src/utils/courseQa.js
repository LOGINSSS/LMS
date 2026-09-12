export const questionCenterTarget = (courseId, questionId) => ({
  path: `/courses/${courseId}/qa`,
  query: questionId == null ? {} : { questionId: String(questionId) }
})

export const filterCourseQuestions = (questions, filter) => {
  const items = Array.isArray(questions) ? questions : []
  if (filter === 'pending') {
    return items.filter((item) => !item.answers?.length)
  }
  if (filter === 'answered') {
    return items.filter((item) => item.answers?.length)
  }
  return items
}

export const notificationPresentation = (notification) => {
  if (Number(notification?.type) === 1) {
    return { label: '待回答', tone: 'pending', action: '进入回答' }
  }
  if (Number(notification?.type) === 3) {
    return { label: '已处理', tone: 'resolved', action: '查看详情' }
  }
  return { label: '已回答', tone: 'answered', action: '查看详情' }
}
