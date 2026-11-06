import { describe, expect, it } from '@jest/globals'

import {
    CalcRequestDataContainerSchema,
    exportRowsToQueryString,
    importRowsFromQueryString,
} from '../../src/types/Calc'
import type { CalcRequestRow } from '../../src/types/Calc'
import { expectParseFail, expectParseOk } from './__parse_okfail'

const validRow = (overrides: Partial<CalcRequestRow> = {}): CalcRequestRow =>
    ({
        prefix: '',
        compoundOrBlend: 'c1',
        dose: 10,
        start: '1d',
        duration: '2h',
        freqName: 'f1',
        group: '0',
        selected: false,
        ...overrides,
    }) as unknown as CalcRequestRow

describe('CalcRequestDataContainerSchema', () => {
    describe('group field', () => {
        it('accepts valid group id 0 for single row', () => {
            expectParseOk(CalcRequestDataContainerSchema, { cycle: [validRow()] })()
        })
        it('accepts valid group ids for multiple rows', () => {
            expectParseOk(CalcRequestDataContainerSchema, {
                cycle: [validRow({ group: '0' }), validRow({ group: '1' })],
            })()
        })
        it('rejects group id out of range (>= cycle length)', () => {
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [validRow({ group: '1' })],
            })()
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [validRow({ group: '0' }), validRow({ group: '2' })],
            })()
        })
        it('rejects non-numeric group string', () => {
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [validRow({ group: 'abc' })],
            })()
        })
        it('rejects negative group', () => {
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [validRow({ group: '-1' })],
            })()
        })
    })

    describe('ramp field', () => {
        it('accepts a complete ramp', () => {
            expectParseOk(CalcRequestDataContainerSchema, {
                cycle: [
                    validRow({
                        doseRampEnabled: true,
                        doseRampDose: 5,
                        doseRampFreqName: 'f2',
                    }),
                ],
            })()
        })
        it('accepts a negative ramp dose', () => {
            expectParseOk(CalcRequestDataContainerSchema, {
                cycle: [
                    validRow({
                        doseRampEnabled: true,
                        doseRampDose: -5,
                        doseRampFreqName: 'f2',
                    }),
                ],
            })()
        })
        it('rejects a zero ramp dose', () => {
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [
                    validRow({
                        doseRampEnabled: true,
                        doseRampDose: 0,
                        doseRampFreqName: 'f2',
                    }),
                ],
            })()
        })
        it('rejects an enabled ramp without both fields', () => {
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [validRow({ doseRampEnabled: true })],
            })()
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [validRow({ doseRampEnabled: true, doseRampDose: 5 })],
            })()
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [validRow({ doseRampEnabled: true, doseRampFreqName: 'f2' })],
            })()
        })
        it('rejects ramp fields when the ramp is disabled', () => {
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [validRow({ doseRampEnabled: false, doseRampDose: 5, doseRampFreqName: 'f2' })],
            })()
        })
        it('rejects a ramp for transformers', () => {
            expectParseFail(CalcRequestDataContainerSchema, {
                cycle: [
                    validRow({
                        prefix: '.t',
                        variantOrTransformer: 't1',
                        doseRampEnabled: true,
                        doseRampDose: 5,
                        doseRampFreqName: 'f2',
                    }),
                ],
            })()
        })
    })

    describe('query string ramp params', () => {
        it('imports ramp params as a pair', () => {
            const q = new URLSearchParams(
                'cb=c1&d=10&s=1d&t=2h&fn=f1&rd=5&rfn=f2',
            )
            const [row] = importRowsFromQueryString(q)
            expect(row.doseRampEnabled).toBe(true)
            expect(row.doseRampDose).toBe(5)
            expect(row.doseRampFreqName).toBe('f2')
        })
        it('imports no ramp when both params are empty', () => {
            const q = new URLSearchParams(
                'cb=c1&d=10&s=1d&t=2h&fn=f1&rd=&rfn=',
            )
            const [row] = importRowsFromQueryString(q)
            expect(row.doseRampEnabled).toBe(false)
            expect(row.doseRampDose).toBeUndefined()
            expect(row.doseRampFreqName).toBeUndefined()
        })
        it('rejects a ramp dose without a ramp frequency', () => {
            const q = new URLSearchParams(
                'cb=c1&d=10&s=1d&t=2h&fn=f1&rd=5&rfn=',
            )
            expect(() => importRowsFromQueryString(q)).toThrow()
        })
        it('rejects a ramp frequency without a ramp dose', () => {
            const q = new URLSearchParams(
                'cb=c1&d=10&s=1d&t=2h&fn=f1&rd=&rfn=f2',
            )
            expect(() => importRowsFromQueryString(q)).toThrow()
        })
        it('rejects mismatched ramp param lengths', () => {
            const q = new URLSearchParams(
                'cb=c1&d=10&s=1d&t=2h&fn=f1&rd=5&rd=6&rfn=f2',
            )
            expect(() => importRowsFromQueryString(q)).toThrow()
        })
        it('exports ramp params as a pair', () => {
            const q = exportRowsToQueryString([
                validRow({
                    doseRampEnabled: true,
                    doseRampDose: 5,
                    doseRampFreqName: 'f2',
                }),
            ])
            expect(q.getAll('rd')).toEqual(['5'])
            expect(q.getAll('rfn')).toEqual(['f2'])
        })
        it('exports empty ramp params when the ramp is disabled', () => {
            const q = exportRowsToQueryString([validRow()])
            expect(q.getAll('rd')).toEqual([''])
            expect(q.getAll('rfn')).toEqual([''])
        })
    })
})
