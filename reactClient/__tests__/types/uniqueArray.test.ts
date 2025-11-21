import { describe, expect, it } from '@jest/globals'
import { z } from 'zod'

import { zodSuperRefinerForUniqueArray } from '../../src/types/uniqueArray'
import { parseError } from './__get_parse_error'

describe('uniqueArray', () => {
    const zSimpleKeyed = z.object({
        k: z.string(),
        v: z.number()
    })
    type zSkRow = z.infer<typeof zSimpleKeyed>

    const zCompoundKeyed = z.object({
        k1: z.string(),
        k2: z.string().optional(),
        v: z.number()
    })
    type zCkRow = z.infer<typeof zCompoundKeyed>

    // zSkA tests Compounds, Blends, Frequencies names
    const zSimpleKeyedArray = z.array(zSimpleKeyed).superRefine(
        zodSuperRefinerForUniqueArray(
            (r: zSkRow) => r.k,
            () => [['k', 'respecified']],
        ),
    )

    // zSkApP tests Blends post
    const zSimpleKeyedArrayPlusPost = z.array(zSimpleKeyed).superRefine(
        zodSuperRefinerForUniqueArray(
            (r: zSkRow) => r.k,
            () => [['k', 'respecified']],
            (seen: readonly string[]) => {
                if (seen.length > 1)
                    return '1'
                return ''
            }
        ),
    )
    // zCkA tests Compounds deleter
    const zCompoundKeyedArray = z.array(zCompoundKeyed).superRefine(
        zodSuperRefinerForUniqueArray(
            (r: zCkRow) => {
                const a = [r.k1]
                if (r.k2) a.push(r.k2)
                return a
            },
            (r: zCkRow) => {
                const a: [string, string][] = []
                a.push(['k1', `k ${r.k2 ? '...': '$'}`])
                if (r.k2) a.push(['k2', '... $'])
                return a
            },
            () => '', // post = noop
            (seen: readonly string[][], cur: string[]) => {
                for (const cmp of seen) {
                    if ((cur[0] === cmp[0]) && (cur[1] === cmp[1]))
                        return true
                }
                return false
            }
        )
    )
    const ska1: zSkRow[] = [
        { k: 'a', v: 1 },
    ]
    const skaNodups: zSkRow[] = [
        ...ska1,
        { k: 'b', v: 2 }
    ]
    const skaWithdups: zSkRow[] = [
        ...skaNodups,
        { k: 'a', v: 3 },
    ]
    const ckaNodups: zCkRow[] = [
        { k1: 'a', v: 1 },
        { k1: 'a', k2: 'b', v: 2 },
    ]
    const ckaWithdups: zCkRow[] = [
        ...ckaNodups,
        { k1: 'a', v: 3 },
        { k1: 'a', k2: 'b', v:  4}
    ]
    describe('should accept without dups', () => {
        it('for simple', () => {
            expect(() => zSimpleKeyedArray.parse(skaNodups)).not.toThrow()
        })
        it('for compound', () => {
            expect(() => zCompoundKeyedArray.parse(ckaNodups)).not.toThrow()
        })
    })
    describe('should reject with dups', () => {
        it('for simple', () => {
            expect(() => zSimpleKeyedArray.parse(skaWithdups)).toThrow()
        })
        it('for compound', () => {
            expect(() => zCompoundKeyedArray.parse(ckaWithdups)).toThrow()
        })
    })
    describe('should call post()', () => {
        it('for pass', () => {
            expect(() => zSimpleKeyedArrayPlusPost.parse(ska1)).not.toThrow()
        })
        it('for fail', () => {
            expect(() => zSimpleKeyedArrayPlusPost.parse(skaNodups)).toThrow()
        })
    })
    describe('should set error nodes', () => {
        it('for simple-key', () => {
            const e = parseError(skaWithdups, zSimpleKeyedArray)
            const eNode = e?.items?.[2]?.properties?.k
            expect(eNode).not.toBeUndefined()
            expect(eNode?.errors?.[0]).toContain('respecified')
        })
        it('for compound-key', () => {
            const e = parseError(ckaWithdups, zCompoundKeyedArray)
            const eNode0 = e?.items?.[2]?.properties?.k1
            const eNode1 = e?.items?.[3]?.properties?.k1
            const eNode2 = e?.items?.[3]?.properties?.k2
            expect(eNode0?.errors?.[0]).toContain('k $')
            expect(eNode1?.errors?.[0]).toContain('k ...')
            expect(eNode2?.errors?.[0]).toContain('... $')
        })
    })
})