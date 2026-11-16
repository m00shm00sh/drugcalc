import { describe, expect, it } from '@jest/globals'
import { z } from 'zod'

import {
    groupOptionValues,
    isAutoNewGroup,
    zodGroupIdString,
    zodSuperRefinerForGroupIds,
} from '../../src/types/groupIds'
import { parseError } from './__get_parse_error'

const zRow = z.object({
    group: z.string(),
})
type ZRow = z.infer<typeof zRow>

const zGroupIds = z.array(zRow).superRefine(
    zodSuperRefinerForGroupIds(
        (r: ZRow) => r.group,
        (r: ZRow) => [['group', `group id ${r.group} out of range`]],
    ),
)

describe('zodSuperRefinerForGroupIds', () => {
    describe('accepts in-range group ids', () => {
        it('single row group 0', () => {
            expect(() => zGroupIds.parse([{ group: '0' }])).not.toThrow()
        })
        it('multiple rows groups 0..n-1', () => {
            expect(() => zGroupIds.parse([{ group: '0' }, { group: '1' }, { group: '2' }])).not.toThrow()
        })
        it('non-sequential but in-range ids', () => {
            expect(() => zGroupIds.parse([{ group: '2' }, { group: '0' }, { group: '1' }])).not.toThrow()
        })
        it('auto-new and copy-previous sentinels', () => {
            expect(() => zGroupIds.parse([{ group: '-1' }, { group: '-2' }])).not.toThrow()
        })
    })

    describe('rejects out-of-range group ids', () => {
        it('negative ids other than the sentinels', () => {
            expect(() => zGroupIds.parse([{ group: '-3' }])).toThrow()
        })
        it('group id equal to array length', () => {
            expect(() => zGroupIds.parse([{ group: '3' }])).toThrow()
        })
        it('group id greater than array length', () => {
            expect(() => zGroupIds.parse([{ group: '0' }, { group: '3' }])).toThrow()
        })
        it('group id in last position out of range', () => {
            expect(() => zGroupIds.parse([{ group: '0' }, { group: '0' }, { group: '3' }])).toThrow()
        })
    })

    describe('sets error nodes', () => {
        it('at [index, group] for out-of-range', () => {
            const e = parseError([{ group: '0' }, { group: '2' }], zGroupIds)
            const eNode = e?.items?.[1]?.properties?.group
            expect(eNode).not.toBeUndefined()
            expect(eNode?.errors?.[0]).toContain('group id 2 out of range')
        })
    })

    describe('returns undefined when no errors', () => {
        it('for valid in-range data', () => {
            const e = parseError([{ group: '0' }, { group: '1' }], zGroupIds)
            expect(e).toBeUndefined()
        })
    })
})

describe('zodGroupIdString', () => {
    it.each(['-1', '-2', '0', '1', '42'])('accepts %s', (group) => {
        expect(zodGroupIdString.parse(group)).toBe(group)
    })

    it.each(['-3', '01', '1.5', '', 'x'])('rejects %s', (group) => {
        expect(() => zodGroupIdString.parse(group)).toThrow()
    })
})

describe('isAutoNewGroup', () => {
    it('recognizes only the auto-new sentinel', () => {
        expect(isAutoNewGroup('-1')).toBe(true)
        expect(isAutoNewGroup('-2')).toBe(false)
        expect(isAutoNewGroup('0')).toBe(false)
    })
})

describe('groupOptionValues', () => {
    it('labels the sentinels and lists one option per row', () => {
        expect(groupOptionValues(2)).toEqual({
            'new': '-1',
            'copy': '-2',
            '0': '0',
            '1': '1',
        })
    })
})
