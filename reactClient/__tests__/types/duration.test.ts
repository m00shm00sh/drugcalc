import { describe, expect, it } from '@jest/globals'
import {
    iso8601ToDisplay,
    displayToIso8601,
    iso8601ToNumber,
    displayToNumber,
} from '../../src/types/duration'

const cases: [iso: string, disp: string, num: number][] = [
    ['PT25H1M1S', '1d1h1m1s', 25*3600+61],
    ['PT1H1M', '1h1m', 3660],
    ['PT1H', '1h', 3600],
    ['PT1H1S', '1h1s', 3601]
]

describe('iso to display', () => {
    for (const [iso, disp, num] of cases) {
        it('should convert to display', () => {
            const got = iso8601ToDisplay(iso)
            expect(got).toBe(disp)
        })
        it('should convert to number', () => {
            const got = iso8601ToNumber(iso)
            expect(got).toBe(num)
        })
    }
    it('should pass-through unprefixed time', () => {
        expect(iso8601ToDisplay('a1b')).toBe('a1b')
    })
    it('should reject invalid iso time', () => {
        expect(() => iso8601ToDisplay('Paabbcc')).toThrow()
    })
})

describe('display to iso', () => {
    for (const [iso, disp, num] of cases) {
        it('should convert to iso', () => {
            const got = displayToIso8601(disp)
            expect(got).toBe(iso)
        })
        it('should convert to number', () => {
            const got = displayToNumber(disp)
            expect(got).toBe(num)
        })
    }
    it('should reject invalid display time', () => {
        expect(() => displayToIso8601('PT1H')).toThrow()
    })
})
