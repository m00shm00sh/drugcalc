import { require } from '../util/util'
import { z } from 'zod'

const DURATION_RX = RegExp(
    '^(' +
    '(?!$)' +
    '((\\d+d)|(\\d+\\.\\d+d$))?' +
    '((?=\\d)' +
    '((\\d+h)|(\\d+\\.\\d+h$))?' +
    '((\\d+m)|(\\d+\\.\\d+m$))?' +
    '(\\d+(\\.\\d+)?s)?)??' +
    ')+$',
)

export const zodDurationString = z
    .string()
    .regex(DURATION_RX, 'invalid duration')

export function iso8601ToDisplay(t: string): string {
    if (t.startsWith('P')) {
        require(t[1] === 'T', 'unsupported: expected time with denormalized hours')
        const m = t.match(/T(\d+)+H(.*)$/)
        if (m) {
            let [, hS, rest] = m
            let h = Number(hS)
            const days = Math.floor(h / 24)
            if (days > 0) {
                rest = rest.toLowerCase()
                h -= days * 24
                hS = ''
                if (h > 0) hS = `${h}h`
                return `${days}d${hS}${rest}`
            }
        }

        t = t.substring(2)
        t = t.toLowerCase()
    }
    return t
}

export function displayToIso8601(t: string): string {
    t = t.toUpperCase()
    return 'PT' + t
}
