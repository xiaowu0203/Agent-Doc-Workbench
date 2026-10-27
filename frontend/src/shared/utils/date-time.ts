/** 历史 LocalDateTime 的来源及统一展示时区，见工程体验时间契约。 */
export const BUSINESS_TIME_ZONE = 'Asia/Shanghai'
const LOCAL_OFFSET = '+08:00'
const formatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: BUSINESS_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

/** 不让浏览器时区猜测无偏移历史值；未知格式保留原文。 */
export function formatBusinessTime(value: string | null | undefined, emptyText = '—'): string {
  if (!value) return emptyText
  const date = parseBusinessTime(value)
  if (!date) return value
  const parts = Object.fromEntries(
    formatter.formatToParts(date).map((part) => [part.type, part.value]),
  )
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second} ${LOCAL_OFFSET}`
}

/** 时间线排序与显示耗时使用同一来源时区，不能混用浏览器本地解析。 */
export function businessTimeMillis(value: string): number {
  return parseBusinessTime(value)?.getTime() ?? Number.NaN
}

function parseBusinessTime(value: string): Date | null {
  const match =
    /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(\.\d{1,9})?(Z|[+-]\d{2}:\d{2})?$/.exec(
      value,
    )
  if (!match) return null
  const [, year, month, day, hour, minute, second, fraction = '', offset = LOCAL_OFFSET] = match
  const daysInMonth = new Date(Date.UTC(Number(year), Number(month), 0)).getUTCDate()
  if (
    Number(month) < 1 ||
    Number(month) > 12 ||
    Number(day) < 1 ||
    Number(day) > daysInMonth ||
    Number(hour) > 23 ||
    Number(minute) > 59 ||
    Number(second) > 59
  )
    return null
  const date = new Date(`${year}-${month}-${day}T${hour}:${minute}:${second}${fraction}${offset}`)
  return Number.isFinite(date.getTime()) ? date : null
}
