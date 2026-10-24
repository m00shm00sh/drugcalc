import { describe, expect, it } from '@jest/globals'

import { mockNextFetch, mockNextFetch404 } from '../__fetch_utils'
import { expectParseFail, expectParseOk } from './__parse_okfail'

import {
    FrequenciesMapSchema,
    FrequencyDeleterDataContainerSchema,
    frequencyDeleterRowInit,
    frequencyEditorComponentItemInit,
    FrequencyEditorComponentItemSchema,
    FrequencyEditorDataContainerSchema,
    frequencyEditorFieldsToMap,
    frequencyEditorRowInit,
    FrequencyEntrySchema,
    frequencyMapToEditorFields,
    loadFrequencyDetailsFromRemote,
    sortedFrequenciesWithWeights
} from '../../src/types/Frequencies'
import type {
    FrequenciesMap,
    FrequencyEditorRow,
    FrequencyEntry
} from '../../src/types/Frequencies'

describe('FrequencyEntry', () => {
    it('should accept valid object',
        expectParseOk(FrequencyEntrySchema,
            { values: ['PT11H', 'PT11M', 'PT22S', 'PT12H34M', 'PT12H45S'] }
        )
    )
    it('should reject object with non-iso duration',
        expectParseFail(FrequencyEntrySchema, { values: ['1h', '1m'] })
    )
})

describe('FrequencyMap', () => {
    it('should accept valid object',
        expectParseOk(FrequenciesMapSchema,
            <FrequenciesMap>{
                'a a': { values: ['PT1H'] },
                'a b': { values: ['PT1H'] }
            })
    )
    it('should reject empty key name',
        expectParseFail(FrequenciesMapSchema, {'': { values: ['PT1H'] }})
    )
})

describe('FrequencyRowComponentItem', () => {
    it('should create an invalid default entry',
        expectParseFail(FrequencyEditorComponentItemSchema,
            frequencyEditorComponentItemInit()
        )
    )
    it('should accept display time',
        expectParseOk(FrequencyEditorComponentItemSchema,
            { value: '1d23h45m6s' }
        )
    )
    it('should reject unexpected or missing time', () => {
        const f = (o: unknown) =>
            expectParseFail(FrequencyEditorComponentItemSchema, { value: o })()
        f('PT1H')
        f('')
        f(1)
    })
})

describe('FrequencyRows', () => {
    it('should create an invalid default row',
        expectParseFail(FrequencyEditorDataContainerSchema,
            { frequencies: [frequencyEditorRowInit()] }
        )
    )
    it('should require unique frequency names',
        expectParseFail(FrequencyEditorDataContainerSchema,
            {frequencies: [
                {frequency: 'a', values: [{value: '1h'}] },
                {frequency: 'a', values: [{value: '1h'}] },
            ]}
        )
    )
    it('should accept unique frequency names',
        expectParseOk(FrequencyEditorDataContainerSchema,
            {frequencies: [
                {frequency: 'a', values: [{value: '1h'}] },
                {frequency: 'b', values: [{value: '1h'}] },
            ]}
        )
    )
})

describe('converter', () => {
    const map: FrequenciesMap = {
        'a b': { values: [ 'PT25H', 'PT1M1S'] },
        'b': { values: [ 'PT1M' ] },
    }
    const rows: FrequencyEditorRow[] = [
        { frequency: 'a b', values: [{value: '1d1h'}, {value: '1m1s'}] },
        { frequency: 'b', values: [{value: '1m'}] }
    ]
    it('for mapToRows', () => {
        expect(frequencyMapToEditorFields(map)).toEqual(rows)
    })
    it('for rowsToMap', () => {
        expect(frequencyEditorFieldsToMap(rows)).toEqual(map)
    })
})

describe('rowLoader', () => {
    it('should return undefined on 404', async () => {
        mockNextFetch404()
        const o = await loadFrequencyDetailsFromRemote({
            ...frequencyEditorRowInit(),
            frequency: 'a',
        })
        expect(o).toBeUndefined()
    })
    it('should fill object on success', async () => {
        const i: FrequencyEditorRow = {
            ...frequencyEditorRowInit(),
            frequency: 'b',
        }
        const e: FrequencyEntry = { values: ['PT25H', 'PT1M'] }
        const o = frequencyMapToEditorFields({ 'b': e })[0]
        // the caching fetcher is unexported so use different param for each test
        mockNextFetch(e)
        const got = await loadFrequencyDetailsFromRemote(i)
        expect(got).toEqual(o)
    })
})

describe('sortByWeight', () => {
    it('should sort the map', () => {
        /* NOTE: the reason we use display instead of iso format durations here is because
         *       components/Calc decodes iso storage format to display format *then*
         *       splices in the decoded remote values with the local values.
         *       Refactoring for the sake of making the schema pass zod validation is
         *       not worth the effort.
         */
        const map: FrequenciesMap = {
            'a b': { values: [ '1d1h', '1m1s'] },
            'b': { values: [ '1m' ] },
        }
        const o = sortedFrequenciesWithWeights(map)
        expect(Object.keys(o)).toEqual(['b', 'a b'])
    })
})

describe('DeleterRows', () => {
    it('should create an invalid default row',
        expectParseFail(FrequencyDeleterDataContainerSchema,
            { frequencies: [frequencyDeleterRowInit()] }
        )
    )
    it('should require unique frequency names',
        expectParseFail(FrequencyDeleterDataContainerSchema,
            {frequencies: [
                {frequency: 'a' },
                {frequency: 'a' },
            ]}
        )
    )
    it('should accept unique frequency names',
        expectParseOk(FrequencyDeleterDataContainerSchema,
            {frequencies: [
                {frequency: 'a' },
                {frequency: 'b' },
            ]}
        )
    )
})