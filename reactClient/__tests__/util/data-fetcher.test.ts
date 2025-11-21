import { describe, expect, it } from '@jest/globals'

import type { CompoundInfo, CompoundName } from '../../src/types/Compounds'

import {
    fetchCompoundDetailsOrNull,
    fetchCompounds,
    fetchVariants,
    cachedRemoteCompounds,
    cachedRemoteVariants,
    cachedRemoteVariantsOrNull,
    cachedRemoteBlends,
    fetchBlends,
    fetchBlendDetailsOrNull,
    cachedRemoteFrequenciesWithWeights,
    fetchFrequencies,
    fetchFrequencyDetailsOrNull,
    cachedTransformerNames,
    cachedTransformerFrequencies,
    compoundPath
} from '../../src/util/data-fetcher'
import { iso8601ToNumber } from '../../src/types/duration'
import {
    checkCacheableFetch,
    checkCacheableFetch404,
    checkCorrectEndpoint,
    checkUncachedFetch,
    lastCallPathItems,
    mockCacheableFetch,
    mockNextFetch404,
    mockUncachedFetch
} from '../__fetch_utils'

const encodePathParameter = (p: string): string => encodeURIComponent(p).replaceAll('%20', '+')

describe('compoundPath', () => {
    it('should urlencode with plus each argument', () => {
        const names = ['ab[c {', 'd=e f]}()'] as CompoundName
        const encodedNames = names.map((e) => encodePathParameter(e))
        expect(compoundPath(names)).toBe(encodedNames.join('/'))
    })
    it('should hyphenize a missing second argument', () => {
        const names = ['a'] as CompoundName
        const encodedNames = [encodePathParameter(names[0]), '-']
        expect(compoundPath(names)).toBe(encodedNames.join('/'))
    })
    it('should not hyphenate a deleter', () => {
        const names = ['a', 'b'] as CompoundName
        const encodedName = encodePathParameter(names[0])
        expect(compoundPath(names, true)).toBe(encodedName)
    })
})

/*
 * NOTE: we do not do schema validation here; that is left to types/
 */

describe('cachedRemoteCompounds', () => {
    it('should call the expected base endpoint', async () => {
        await mockCacheableFetch(cachedRemoteCompounds, () => cachedRemoteCompounds(), [])
        checkCorrectEndpoint(['compounds'])
    })
    it('should return a list of compounds', () =>
        checkCacheableFetch(cachedRemoteCompounds, () => cachedRemoteCompounds(), ['a', 'b'])
    )
    it('should return an empty list on 404', () =>
        checkCacheableFetch404(cachedRemoteCompounds, () => cachedRemoteCompounds(), [])
    )
})

describe('fetchCompounds', () => {
    it('should merge compound names', () =>
        checkCacheableFetch(cachedRemoteCompounds, () => fetchCompounds({'b':[], 'c':[]}), ['a', 'b'], ['a', 'b', 'c'])
    )
    it('should return remote compounds when no locals',() =>
        checkCacheableFetch(cachedRemoteCompounds, () => fetchCompounds({}), ['a', 'b'])
    )
})

describe('cachedRemoteVariants', () => {
    it('should call the expected base endpoint', async () => {
        await mockCacheableFetch(cachedRemoteVariants, () => cachedRemoteVariants('a'), [])
        checkCorrectEndpoint(['compounds'])
    })
    it('should urlencode with plus the compound component', async () => {
        const inpCompound = 'abc[de/f g'
        const expCompound = encodeURIComponent(inpCompound).replaceAll('%20', '+')
        await mockCacheableFetch(cachedRemoteVariants, () => cachedRemoteVariants(inpCompound), [])
        await cachedRemoteVariants(inpCompound)
        const gotCompound = lastCallPathItems(1)[0]
        expect(gotCompound).toBe(expCompound)
    })
    it('should return a non-empty list of variants for a compound that exists', () =>
        checkCacheableFetch(cachedRemoteVariants, () => cachedRemoteVariants('a'), ['c', 'd'])
    )
    it('should return an empty list of variants for a compound that exists', () =>
        checkCacheableFetch(cachedRemoteVariants, () => cachedRemoteVariants('b'), [])
    )
    it('should return an empty list on 404', () =>
        checkCacheableFetch404(cachedRemoteVariants, () => cachedRemoteVariants('c'), [])
    )
})

describe('cachedRemoteVariantsOrNull', () => {
    it('should call the expected base endpoint', async () => {
        await mockCacheableFetch(cachedRemoteVariantsOrNull, () => cachedRemoteVariantsOrNull('a'), [])
        checkCorrectEndpoint(['compounds'])
    })
    it('should urlencode with plus the compound component', async () => {
        const inpCompound = 'abc[de/f g'
        const expCompound = encodeURIComponent(inpCompound).replaceAll('%20', '+')
        await mockCacheableFetch(cachedRemoteVariantsOrNull, () => cachedRemoteVariantsOrNull(inpCompound), [])
        await cachedRemoteVariantsOrNull(inpCompound)
        const gotCompound = lastCallPathItems(1)[0]
        expect(gotCompound).toBe(expCompound)
    })
    it('should return undefined on 404', () =>
        checkCacheableFetch404(cachedRemoteVariantsOrNull, () => cachedRemoteVariantsOrNull('a'))
    )
    it('should return a non-empty list of variants for a compound that exists', () =>
        checkCacheableFetch(cachedRemoteVariantsOrNull, () => cachedRemoteVariantsOrNull('a'), ['c', 'd'])
    )
    it('should return an empty list of variants for a compound that exists', () =>
        checkCacheableFetch(cachedRemoteVariantsOrNull, () => cachedRemoteVariantsOrNull('b'), [])
    )
})

describe('fetchVariants', () => {
    it('should merge variant names', () =>
        checkCacheableFetch(cachedRemoteVariants, () => fetchVariants({'b':['c']}, 'b'), ['a', 'b'], ['a', 'b', 'c'])
    )
    it('should return remote variants when no locals', () =>
        checkCacheableFetch(cachedRemoteVariants, () => fetchVariants({'c': []}, 'a'), ['a', 'b'])
    )
    it('should return remote variants when empty locals', () =>
        checkCacheableFetch(cachedRemoteVariants, () => fetchVariants({'c': []}, 'c'), ['a', 'b'])
    )
    it('should return empty list for no compound', async () => {
        await expect(() => fetchVariants({}, '')).resolves.toEqual([])
    })
})

// NOTE: this is an uncached lower-level fetcher; there is no need to manage the cache during mocking
describe('fetchCompoundDetailsOrNull', () => {
    it('should require nonempty compound name', async () => {
        await expect(() => fetchCompoundDetailsOrNull([''])).rejects.toThrow('invalid compound name')
    })
    it('should call the expected base endpoint', async () => {
        await mockUncachedFetch(() => fetchCompoundDetailsOrNull(['a']))
        checkCorrectEndpoint(['compounds'])
    })
    it('should urlencode with plus each argument', async () => {
        const names = ['ab[c {', 'd=e f]}()'] as CompoundName
        const encodedNames = names.map((e) => encodePathParameter(e))
        await checkUncachedFetch(() => fetchCompoundDetailsOrNull(names), {
            halfLife: 'PT120H',
            pctActive: 0.69,
            note: 'test'
        })
        const components = lastCallPathItems(2)
        expect(components).toEqual(encodedNames)
    })
    it('should hyphenize a missing second argument', async () => {
        const names = ['a'] as CompoundName
        await checkUncachedFetch(() => fetchCompoundDetailsOrNull(names), {
            halfLife: 'PT120H',
            pctActive: 0.69,
            note: 'test'
        })
        const c2 = lastCallPathItems(2)[1]
        expect(c2).toBe('-')
    })
    it('should return a valid object', () =>
        checkUncachedFetch(() => fetchCompoundDetailsOrNull(['a']), {
            halfLife: 'PT120H',
            pctActive: 0.69,
            note: 'test'
        })
    )
    it('should normalize missing optional fields', async () => {
        const obj: CompoundInfo = {
            halfLife: 'PT120H',
            note: 'test'
        }
        const o2: CompoundInfo = {...obj, pctActive: 1.0}
        await checkUncachedFetch(() => fetchCompoundDetailsOrNull(['a']), obj, o2)
    })
    it('should return null on 404', async () => {
        const names = ['a'] as CompoundName
        mockNextFetch404()
        await expect(fetchCompoundDetailsOrNull(names)).resolves.toBeUndefined()
    })
})

describe('cachedRemoteBlends', () => {
    it('should call the expected base endpoint', async () => {
        await mockCacheableFetch(cachedRemoteBlends, () => cachedRemoteBlends(), [])
        checkCorrectEndpoint(['blends'])
    })
    it('should return a list of blends', () =>
        checkCacheableFetch(cachedRemoteBlends, () => cachedRemoteBlends(), ['a', 'b'])
    )
    it('should return an empty list on 404', () =>
        checkCacheableFetch404(cachedRemoteBlends, () => cachedRemoteBlends(), [])
    )
})

describe('fetchCompounds', () => {
    it('should merge compound names', () =>
        checkCacheableFetch(cachedRemoteCompounds, () => fetchCompounds({'b':[], 'c':[]}), ['a', 'b'], ['a', 'b', 'c'])
    )
    it('should return remote compounds when no locals', () =>
        checkCacheableFetch(cachedRemoteCompounds, () => fetchCompounds({}), ['a', 'b'])
    )
})

describe('cachedBlends', () => {
    it('should merge blend names', () =>
        checkCacheableFetch(cachedRemoteBlends, () => fetchBlends(['b', 'c']), ['a', 'b'], ['a', 'b', 'c'])
    )
    it('should return remote compounds when no locals', () =>
        checkCacheableFetch(cachedRemoteBlends, () => fetchBlends([]), ['a', 'b'])
    )
})

// NOTE: this is an uncached lower-level fetcher; there is no need to manage the cache during mocking
describe('fetchBlendDetailsOrNull', () => {
    it('should require nonempty blend name', async () => {
        await expect(() => fetchBlendDetailsOrNull('')).rejects.toThrow('invalid blend name')
    })
    it('should call the expected base endpoint', async () => {
        await mockUncachedFetch(() => fetchBlendDetailsOrNull('a'))
        checkCorrectEndpoint(['blends'])
    })
    it('should urlencode with plus each argument', async () => {
        const name = 'a b=c['
        const encodedName = encodePathParameter(name)
        await checkUncachedFetch(() => fetchBlendDetailsOrNull(name), {
            components: {
                'fred=bar': 1.0,
                'baz': 2.0,
            },
            note: 'test'
        })
        const component = lastCallPathItems(1)[0]
        expect(component).toBe(encodedName)
    })
    it('should return a valid object', () =>
        checkUncachedFetch(() => fetchBlendDetailsOrNull('a'), {
            components: {
                'fred=bar': 1.0,
                'baz': 2.0,
            },
            note: 'test'
        })
    )
    it('should return null on 404', async () => {
        mockNextFetch404()
        await expect(fetchBlendDetailsOrNull('a')).resolves.toBeUndefined()
    })
})

describe('cachedRemoteFrequenciesWithWeights', () => {
    it('should call the expected base endpoint', async () => {
        await mockCacheableFetch(cachedRemoteFrequenciesWithWeights, () => cachedRemoteFrequenciesWithWeights(), {})
        checkCorrectEndpoint(['frequencies.w'])
    })
    it('should return a map of of frequencies and weights', () =>
        checkCacheableFetch(cachedRemoteFrequenciesWithWeights, () => cachedRemoteFrequenciesWithWeights(),
            {
                'a': 'PT1H1S', 'b': 'PT1S'
            },
            { 'a': 3601, 'b': 1 }
        )
    )
    it('should return empty map on 404', () =>
        checkCacheableFetch404(cachedRemoteFrequenciesWithWeights, () => cachedRemoteFrequenciesWithWeights(), {})
    )
})

describe('fetchFrequencies', () => {
    // the result of fetchFrequencies is a list without the sort key; we need the local key to modify order
    it('should merge remote with local override', () =>
        checkCacheableFetch(cachedRemoteFrequenciesWithWeights, () => fetchFrequencies({
            'c': iso8601ToNumber('PT1H1M59S'),
            'b': iso8601ToNumber('PT1H1M'),
        }),
        {
            'a': 'PT1H1S',
            'b': 'PT1S'
        },
        ['a', 'b', 'c']
        )
    )

    it('should return remote when no locals', () =>
        checkCacheableFetch(cachedRemoteFrequenciesWithWeights, () => fetchFrequencies({}),
            {
                'a': 'PT1H1S',
                'b': 'PT1S'
            },
            ['b', 'a']
        )
    )
})

// NOTE: this is an uncached lower-level fetcher; there is no need to manage the cache during mocking
describe('fetchFrequencyDetailsOrNull', () => {
    it('should require nonempty frequency name', async () => {
        await expect(() => fetchFrequencyDetailsOrNull('')).rejects.toThrow('invalid frequency name')
    })
    it('should call the expected base endpoint', async () => {
        await mockUncachedFetch(() => fetchFrequencyDetailsOrNull('a'))
        checkCorrectEndpoint(['frequencies'])
    })
    it('should urlencode with plus each argument', async () => {
        const name = 'a b=c['
        const encodedName = encodePathParameter(name)
        await checkUncachedFetch(() => fetchFrequencyDetailsOrNull(name),
            { values: ['PT69H', 'PT1H6M', 'PT1S'] }
        )
        const component = lastCallPathItems(1)[0]
        expect(component).toBe(encodedName)
    })
    it('should return a valid object', () =>
        checkUncachedFetch(() => fetchFrequencyDetailsOrNull('a'),
            { values: ['PT69H', 'PT1H6M', 'PT1S'] }
        )
    )
    it('should return null on 404', async () => {
        mockNextFetch404()
        await expect(fetchFrequencyDetailsOrNull('a')).resolves.toBeUndefined()
    })
})

describe('cachedTransformerNames', () => {
    it('should call the expected endpoint', async () => {
        await mockCacheableFetch(cachedTransformerNames, () => cachedTransformerNames(), [])
        checkCorrectEndpoint(['transformers', 'names'])
    })
    it('should return a valid object', () =>
        checkCacheableFetch(cachedTransformerNames, () => cachedTransformerNames(), ['a'])
    )
})

describe('cachedTransformerFrequencies', () => {
    it('should call the expected endpoint', async () => {
        await mockCacheableFetch(cachedTransformerFrequencies, () => cachedTransformerFrequencies(), [])
        checkCorrectEndpoint(['transformers', 'frequencies'])
    })
    it('should return a valid object', () =>
        checkCacheableFetch(cachedTransformerFrequencies, () => cachedTransformerFrequencies(), ['a'])
    )
})
