import { describe, expect, it } from '@jest/globals'

import { mockNextFetch, mockNextFetch404 } from '../__fetch_utils'
import { expectParseFail, expectParseOk } from './__parse_okfail'

/* We need to force-import data-fetcher to remove the ReferenceError from
 * importing types/Compounds importing util/data-fetcher import types/Blends importing types/Compounds
 */
import '../../src/util/data-fetcher'

import {
    CompoundDeleterDataContainerSchema,
    compoundDeleterRowInit,
    CompoundEditorDataContainerSchema,
    compoundEditorFieldsToMap,
    compoundEditorRowInit,
    CompoundEditorRowSchema,
    CompoundInfoSchema,
    compoundMapToEditorFields,
    CompoundsMapSchema,
    loadCompoundDetailsFromRemote,
    packCompoundName,
    reshapeCompoundKeys,
    unpackCompoundName
} from '../../src/types/Compounds'
import type {
    ByCompoundByVariant,
    CompoundDeleterDataContainer,
    CompoundEditorRow,
    CompoundInfo,
    CompoundName,
    CompoundsMap
} from '../../src/types/Compounds'

describe('CompoundInfo', () => {
    it('should accept valid object',
        expectParseOk(CompoundInfoSchema,
            <CompoundInfo>{
                halfLife: 'PT25H',
            }
        )
    )
    it('should accept valid object with pctactive',
        expectParseOk(CompoundInfoSchema,
            <CompoundInfo>{
                halfLife: 'PT25H',
                pctActive: 0.5,
            }
        )
    )
    it('should accept valid object with null pctactive',
        expectParseOk(CompoundInfoSchema,
            <CompoundInfo>{
                halfLife: 'PT25H',
                pctActive: undefined,
            }
        )
    )
    it('should accept valid object with note',
        expectParseOk(CompoundInfoSchema,
            <CompoundInfo>{
                halfLife: 'PT25H',
                note: 'a'
            }
        )
    )
    it('should reject invalid halflife',
        expectParseFail(CompoundInfoSchema, { halfLife: '1d1h' } )
    )
    it('should reject invalid pctactive', () => {
        expectParseFail(CompoundInfoSchema, { halfLife: 'PT1H', pctActive: 0 })()
        expectParseFail(CompoundInfoSchema, { halfLife: 'PT1H', pctActive: 100 })()
        expectParseFail(CompoundInfoSchema, { halfLife: 'PT1H', pctActive: '0' })()
    })
})

describe('CompoundsMap', () => {
    it('should accept valid object',
        expectParseOk(CompoundsMapSchema,
            <CompoundsMap>{
                'a': {halfLife: 'PT1H'},
                'b': {halfLife: 'PT1H'}
            })
    )
    it('should reject empty key name',
        expectParseFail(CompoundsMapSchema, {'': {}})
    )
})

describe('CompoundRow', () => {
    it('should create an invalid default entry',
        expectParseFail(CompoundEditorRowSchema, compoundEditorRowInit())
    )
    it('should accept valid compound and halflife and any valid form of variant and pctactive', () => {
        const ok = (o: unknown) => expectParseOk(CompoundEditorRowSchema, <CompoundEditorRow>o)()
        ok({ compound: 'a', halfLife: '1h', pctActive: 100 })
        ok({ compound: 'a', variant: '', halfLife: '1h', pctActive: 100 })
        ok({ compound: 'a', halfLife: '1h', pctActive: 50 })
    })
    it('should reject empty compound',
        expectParseFail(CompoundEditorRowSchema,
            { compound: '', halfLife: '1h' }
        )
    )
    it('should reject missing compound',
        expectParseFail(CompoundEditorRowSchema,
            { halfLife: '1h' }
        )
    )
})

describe('CompoundRows', () => {
    it('should create an invalid default row',
        expectParseFail(CompoundEditorDataContainerSchema,
            { compounds: [compoundEditorRowInit()] }
        )
    )
    it('should require unique (compound,variant) names', () => {
        expectParseFail(CompoundEditorDataContainerSchema,
            {compounds:[
                {compound: 'a', halfLife: '1h', pctActive: 100},
                {compound: 'a', halfLife: '1h', pctActive: 100},
            ]}
        )()
        expectParseFail(CompoundEditorDataContainerSchema,
            {compounds:[
                {compound: 'a', variant: 'b', halfLife: '1h', pctActive: 100},
                {compound: 'a', variant: 'b', halfLife: '1h', pctActive: 100},
            ]}
        )()
    })
    it('should accept unique (compound,variant) names',
        expectParseOk(CompoundEditorDataContainerSchema,
            {compounds:[
                {compound: 'a', halfLife: '1h', pctActive: 100},
                {compound: 'a', variant: 'b', halfLife: '1h', pctActive: 100},
            ]}
        )
    )
})

describe('(un)packer', () => {
    const tab: [string, CompoundName][] = [
        ['a', ['a']],
        ['a=b', ['a', 'b']],
    ]
    for (const [s, n] of tab) {
        it('should pack', () => expect(unpackCompoundName(s)).toEqual(n))
        it('should unpack', () => expect(packCompoundName(n)).toEqual(s))
    }
    // these have irregular forms so can't bidirectionally test them
    it('should unpack alternate empty-variant',
        () => expect(unpackCompoundName('a=')).toEqual(['a'])
    )
    it('should pack alternate empty variant',
        () => expect(packCompoundName(['a', ''])).toEqual('a')
    )
    it('should fail unpacking empty compound', () => expect(() => unpackCompoundName('')).toThrow())
})

describe('reshaper', () => {
    const cm = {
        'a': {},
        'a=a': {},
        'a=b': {},
        'b=c': {},
        'd': {},
    } as unknown as CompoundsMap
    const bcbv: ByCompoundByVariant = {
        'a': ['a', 'b'],
        'b': ['c'],
        'd': []
    }
    it('should reshape', () => expect(reshapeCompoundKeys(cm)).toEqual(bcbv))
})

describe('converter', () => {
    const map: CompoundsMap = {
        'a': { halfLife: 'PT25H1M' },
        // all optional (variant, pctactive, note)
        'b=c': { halfLife: 'PT1H1S', pctActive: 0.5, note: 'a' }
    }
    const rows: CompoundEditorRow[] = [
        { compound: 'a', halfLife: '1d1h1m', pctActive: 100 },
        // all optional (variant, pctactive, note)
        { compound: 'b', variant: 'c', halfLife: '1h1s', pctActive: 50, note: 'a' }
    ]
    it('for mapToRows', () => {
        expect(compoundMapToEditorFields(map)).toEqual(rows)
    })
    it('for rowsToMap', () => {
        expect(compoundEditorFieldsToMap(rows)).toEqual(map)
    })
})

describe('rowLoader', () => {
    it('should return undefined on 404', async () => {
        mockNextFetch404()
        const o = await loadCompoundDetailsFromRemote({
            ...compoundEditorRowInit(),
            compound: 'a',
        })
        expect(o).toBeUndefined()
    })
    it('should fill mandatory fields of object on success', async () => {
        const i: CompoundEditorRow = {
            ...compoundEditorRowInit(),
            compound: 'b',
        }
        const e: CompoundInfo = { halfLife: 'PT1H' }
        const o: CompoundEditorRow = {
            compound: 'b',
            halfLife: '1h',
            pctActive: 100,
        }
        // the caching fetcher is unexported so use different param for each test
        mockNextFetch(e)
        const got = await loadCompoundDetailsFromRemote(i)
        expect(got).toEqual(o)
    })
    it('should fill all fields of object on success', async () => {
        const i: CompoundEditorRow = {
            ...compoundEditorRowInit(),
            compound: 'b',
            variant: 'c'
        }
        const e: CompoundInfo = {
            halfLife: 'PT1M',
            pctActive: 0.5,
            note: 'a'
        }
        const o: CompoundEditorRow = {
            compound: 'b',
            variant: 'c',
            halfLife: '1m',
            pctActive: 50,
            note: 'a'
        }
        // the caching fetcher is unexported so use different param for each test
        mockNextFetch(e)
        const got = await loadCompoundDetailsFromRemote(i)
        expect(got).toEqual(o)
    })
})

describe('DeleterRows', () => {
    it('should create an invalid default row',
        expectParseFail(CompoundDeleterDataContainerSchema,
            { compounds: [compoundDeleterRowInit()] }
        )
    )
    it('should require unique compound matches', () => {
        expectParseFail(CompoundDeleterDataContainerSchema,
            { compounds: [
                { compound: 'a', expand: true },
                { compound: 'a' }
            ] }
        )()
        expectParseFail(CompoundDeleterDataContainerSchema,
            { compounds: [
                { compound: 'a', expand: true },
                { compound: 'a', variant: 'a' }
            ] }
        )()
        expectParseFail(CompoundDeleterDataContainerSchema,
            { compounds: [
                { compound: 'a' },
                { compound: 'a' }
            ] }
        )()
    })
    it('should accept unique compound matches',
        expectParseOk(CompoundDeleterDataContainerSchema,
            <CompoundDeleterDataContainer>{compounds:[
                { compound: 'a' },
                { compound: 'a', variant: 'a' },
                { compound: 'b', expand: true },
            ]}
        )
    )
})