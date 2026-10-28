// 15-minute slots during clinic hours (08:00 - 19:00)
export function timeSlots(): string[] {
  const slots: string[] = []
  for (let h = 8; h < 19; h++) {
    for (let m = 0; m < 60; m += 15) {
      slots.push(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`)
    }
  }
  return slots
}
