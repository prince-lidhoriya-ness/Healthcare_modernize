export function formatDateTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleString('en-GB', {
    weekday: 'short',
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

export function formatDate(isoDate: string): string {
  const [y, m, d] = isoDate.split('-')
  return `${d}/${m}/${y}`
}

export function statusLabel(status: string): string {
  return status === 'NO_SHOW' ? 'No-show' : status.charAt(0) + status.slice(1).toLowerCase()
}

export function interestLabel(code: string): string {
  const labels: Record<string, string> = {
    FERTILITY_ASSESSMENT: 'Fertility assessment',
    IVF: 'IVF',
    IUI: 'IUI',
    EGG_FREEZING: 'Egg freezing',
    EGG_DONATION: 'Egg donation',
  }
  return labels[code] || code
}

export function specialtyLabel(code: string): string {
  const labels: Record<string, string> = {
    REPRODUCTIVE_MEDICINE: 'Reproductive medicine',
    GYNAECOLOGY: 'Gynaecology',
    EMBRYOLOGY: 'Embryology',
    ANDROLOGY: 'Andrology',
    REPRODUCTIVE_IMAGING: 'Reproductive imaging',
  }
  return labels[code] || code
}
