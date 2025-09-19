import { z } from 'zod'

// days=[3] hrs=[6] mins=[9] secs=[12]
const DURATION_RX = RegExp(
    '^(' +
        '(?!$)' +
        '((?=\\d)' +
        '((\\d+d)|(\\d+\\.\\d+d$))?' +
        '((\\d+h)|(\\d+\\.\\d+h$))?' +
        '((\\d+m)|(\\d+\\.\\d+m$))?' +
        '(\\d+(\\.\\d+)?s)?)??' +
        ')+$',
)

// hrs=[3] mins=[6] secs=[9]
const ISO8601_TIME_RX = RegExp(
    '^PT(' +
        '(?!$)' +
        '((?=\\d)((\\d+H)|(\\d+\\.\\d+H$))?' +
        '((\\d+M)|(\\d+\\.\\d+M$))?' +
        '(\\d+(\\.\\d+)?S)?)??' +
        ')+$',
)

export const zodDisplayDurationStringSchema = z
    .string()
    .regex(DURATION_RX, 'invalid duration')

export const zodIsoDurationStringSchema = z
    .string()
    .regex(ISO8601_TIME_RX, 'invalid duration')

const iso8601Tokens = (t: string): [string, string, string] => {
    const m = t.match(ISO8601_TIME_RX)
    if (!m) throw Error('bad format')
    const h_ = (m[3] ?? '0').replace('H', '')
    const m_ = (m[6] ?? '0').replace('M', '')
    const s_ = (m[9] ?? '0').replace('S', '')
    return [h_, m_, s_]
}

const displayTokens = (t: string): [string, string, string, string] => {
    const m = t.match(DURATION_RX)
    if (!m) throw Error('bad format')
    const d_ = (m[3] ?? '0').replace('d', '')
    const h_ = (m[6] ?? '0').replace('h', '')
    const m_ = (m[9] ?? '0').replace('m', '')
    const s_ = (m[12] ?? '0').replace('s', '')
    return [d_, h_, m_, s_]
}

export const iso8601ToDisplay = (t: string): string => {
    if (!t.startsWith('P')) return t
    // eslint-disable-next-line prefer-const
    let [h_, m_, s_] = iso8601Tokens(t).map((e) => Number(e))
    const d_ = Math.floor(h_ / 24)
    h_ -= d_ * 24

    return (
        (d_ > 0 ? `${d_}d` : '') +
        (h_ > 0 ? `${h_}h` : '') +
        (m_ !== 0 ? `${m_}m` : '') +
        (s_ !== 0 ? `${s_}s` : '')
    )
}

export const displayToIso8601 = (t: string): string => {
    // eslint-disable-next-line prefer-const
    let [d_, h_, m_, s_] = displayTokens(t)

    if (d_ !== '0') {
        h_ = `${Number(h_) + Number(d_) * 24}`
        d_ = '0'
    }
    return (
        'PT' +
        (h_ !== '0' ? `${h_}H` : '') +
        (m_ !== '0' ? `${m_}M` : '') +
        (s_ !== '0' ? `${s_}S` : '')
    )
}

export const iso8601ToNumber = (t: string): number => {
    const [h_, m_, s_] = iso8601Tokens(t).map((e) => Number(e))
    return h_ * 3600 + m_ * 60 + s_
}

export const displayToNumber = (t: string): number => {
    const [d_, h_, m_, s_] = displayTokens(t).map((e) => Number(e))
    return d_ * 86400 + h_ * 3600 + m_ * 60 + s_
}
