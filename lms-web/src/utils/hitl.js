const HITL_STATUS = {
  0: 'PENDING',
  1: 'APPROVED',
  2: 'REJECTED'
}

export const normalizeHitlStatus = (status) => {
  const value = String(status ?? '').trim().toUpperCase()
  if (value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED') {
    return value
  }
  return HITL_STATUS[value] || 'UNKNOWN'
}
