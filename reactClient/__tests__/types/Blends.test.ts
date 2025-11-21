import { describe, expect, it } from '@jest/globals'

import { mockNextFetch, mockNextFetch404 } from '../__fetch_utils'
import { expectParseFail, expectParseOk } from './__parse_okfail'

import {
    blendComponentRowInit,
    BlendDeleterDataContainerSchema,
    blendDeleterRowInit,
    BlendEditorComponentRowSchema,
    BlendEditorDataContainerSchema,
    blendEditorFieldsToMap,
    BlendEntrySchema,
    blendMapToEditorFields,
    blendRowInit,
    BlendsMapSchema,
    loadBlendDetailsFromRemote,
} from '../../src/types/Blends'
import type {
    BlendEditorComponentRow,
    BlendEditorRow,
    BlendEntry,
    BlendsMap,
} from '../../src/types/Blends'

describe('BlendEntry', () => {
    it('should accept valid object',
        expectParseOk(BlendEntrySchema,
            <BlendEntry>{ components: {
                'a': 1,
                'a=b': 1
            }}
        )
    )
    it('should accept valid object with note',
        expectParseOk(BlendEntrySchema,
            <BlendEntry>{
                note: '',
                components: {
                    'a': 1,
                    'a=b': 1
                }
            }
        )
    )
    it('should reject object with less than two components',
        expectParseFail(BlendEntrySchema,
            { components: { 'a': 1 } }
        )
    )
    it('should reject object with non-positive dose',
        expectParseFail(BlendEntrySchema,
            { components: {
                'a': 1,
                'a=b': 0,
            } }
        )
    )
})

describe('BlendMap', () => {
    it('should accept valid object',
        expectParseOk(BlendsMapSchema,
            <BlendsMap>{
                'a': { components: {
                    'a': 1,
                    'b=c': 2
                }},
                'b c': { components: {
                    'a': 1,
                    'b=c': 2
                }},
            })
    )
    it('should reject empty key name',
        expectParseFail(BlendsMapSchema, {'': { components: {}}})
    )
})

describe('BlendRowComponentItem', () => {
    it('should create an invalid default entry',
        expectParseFail(BlendEditorComponentRowSchema,
            blendComponentRowInit()
        )
    )
    it('should accept valid compound and dose and any valid form of variant', () => {
        const ok = (o: unknown) => expectParseOk(BlendEditorComponentRowSchema, <BlendEditorComponentRow>o)()
        ok({ compound: 'a', dose: 1})
        ok({ compound: 'a', variant: '', dose: 1 })
        ok({ compound: 'a', variant: 'b', dose: 1 })
    })
    it('should reject empty compound',
        expectParseFail(BlendEditorComponentRowSchema,
            { compound: '', dose: 1 }
        )
    )
    it('should reject missing compound',
        expectParseFail(BlendEditorComponentRowSchema,
            { dose: 1 }
        )
    )
})

describe('BlendRows', () => {
    it('should create an invalid default row',
        expectParseFail(BlendEditorDataContainerSchema,
            { blends: [blendRowInit()] }
        )
    )
    it('should require unique blend names',
        expectParseFail(BlendEditorDataContainerSchema,
            {blends: [
                {blend: 'a', components: [
                    {compound: 'a', dose: 1},
                    {compound: 'b', dose: 1},
                ]},
                {blend: 'a', components: [
                    {compound: 'a', dose: 1},
                    {compound: 'b', dose: 1},
                ]},
            ]}
        )
    )
    it('should accept unique frequency names',
        expectParseOk(BlendEditorDataContainerSchema,
            {blends: [
                {blend: 'a', components: [
                    {compound: 'a', dose: 1},
                    {compound: 'b', dose: 1},
                ]},
                {blend: 'b', components: [
                    {compound: 'a', dose: 1},
                    {compound: 'b', dose: 1},
                ]},
            ]}
        )
    )
    it('should reject duplicate compound',
        expectParseFail(BlendEditorDataContainerSchema,
            {blends: [
                {blend: 'a', components: [
                    {compound: 'a', dose: 1},
                    {compound: 'a', dose: 1},
                ]}
            ]}
        )
    )
})

describe('converter', () => {
    const map: BlendsMap = {
        'a b': { components: {
            'a': 1,
            'a=b': 2
        }},
        'b': { note: 'aa', components: {
            'a': 1.5,
            'b=a': 1.5
        }}
    }
    const rows: BlendEditorRow[] = [
        { blend: 'a b', components: [
            {compound: 'a', dose: 1},
            {compound: 'a', variant: 'b', dose: 2}
        ]},
        { blend: 'b', note: 'aa', components: [
            {compound: 'a', dose: 1.5},
            {compound: 'b', variant: 'a', dose: 1.5}
        ]},
    ]
    it('for mapToRows', () => {
        expect(blendMapToEditorFields(map)).toEqual(rows)
    })
    it('for rowsToMap', () => {
        expect(blendEditorFieldsToMap(rows)).toEqual(map)
    })
})

describe('rowLoader', () => {
    it('should return undefined on 404', async () => {
        mockNextFetch404()
        const o = await loadBlendDetailsFromRemote({
            ...blendRowInit(),
            blend: 'a',
        })
        expect(o).toBeUndefined()
    })
    it('should fill required entries of object on success', async () => {
        const i: BlendEditorRow = {
            ...blendRowInit(),
            blend: 'b',
        }
        const e: BlendEntry = { components: {
            'a': 1,
            'a=b': 2,
        }}
        const o = blendMapToEditorFields({ 'b': e })[0]
        // the caching fetcher is unexported so use different param for each test
        mockNextFetch(e)
        const got = await loadBlendDetailsFromRemote(i)
        expect(got).toEqual(o)
    })
    it('should fill all entries of object on success', async () => {
        const i: BlendEditorRow = {
            ...blendRowInit(),
            blend: 'c',
        }
        const e: BlendEntry = { note: 'aa', components: {
            'a': 1,
            'a=b': 2,
        }}
        const o = blendMapToEditorFields({ 'c': e })[0]
        // the caching fetcher is unexported so use different param for each test
        mockNextFetch(e)
        const got = await loadBlendDetailsFromRemote(i)
        expect(got).toEqual(o)
    })
})

describe('DeleterRows', () => {
    it('should create an invalid default row',
        expectParseFail(BlendDeleterDataContainerSchema,
            { blends: [blendDeleterRowInit()] }
        )
    )
    it('should require unique frequency names',
        expectParseFail(BlendDeleterDataContainerSchema,
            {blends: [
                {blend: 'a' },
                {blend: 'a' },
            ]}
        )
    )
    it('should accept unique frequency names',
        expectParseOk(BlendDeleterDataContainerSchema,
            {blends: [
                {blend: 'a' },
                {blend: 'b' },
            ]}
        )
    )
})