import { z } from 'zod'
import { BlendEntrySchema, BlendNamesListSchema, type BlendEntry } from '../types/Blends'
import {
    CompoundInfoSchema,
    CompoundNamesListSchema,
    VariantNamesListSchema,
    type ByCompoundByVariant,
    type CompoundInfo,
    type CompoundName,
} from '../types/Compounds'
import { iso8601ToNumber } from '../types/duration'
import {
    FrequencyEntrySchema,
    FrequencyNamesWithWeightsSchema,
    type FrequencyEntry,
} from '../types/Frequencies'
import { zodNonemptyStringArraySchema } from '../types/string'
import cache from './cache'
import { requireTrue, type Nullable } from './util'

import { fetchJson } from './fetcher'

function merge<T>(remote: readonly T[], local: readonly T[], sortBy?: (a: T, b: T) => number): T[] {
    const data = [...remote, ...local]
    data.sort(sortBy)
    return data.filterDistinct()
}

export const cachedRemoteCompounds = cache(
    async () => await fetchJson('/api/data/compounds', CompoundNamesListSchema, []),
    () => '',
    60000,
)
export const fetchCompounds = async (bcbv: ByCompoundByVariant): Promise<string[]> =>
    merge(await cachedRemoteCompounds(), Object.keys(bcbv))

// exported for components/BlendsEditor/VariantsFetcherContext
export const cachedRemoteVariants = cache(
    async (cName: string) => {
        requireTrue(!!cName, 'unexpected empty cName')
        const b = encodeURIComponent(cName).replace('%20', '+')
        return await fetchJson(`/api/data/compounds/${b}`, VariantNamesListSchema, [])
    },
    (s: string) => s,
    60000,
)

// we need to tell valid empty list and no such compound apart for CompoundsDeleter
export const cachedRemoteVariantsOrNull = cache(
    async (cName: string) => {
        requireTrue(!!cName, 'unexpected empty cName')
        const b = encodeURIComponent(cName).replace('%20', '+')
        return await fetchJson(`/api/data/compounds/${b}`, VariantNamesListSchema)
    },
    (s: string) => s,
    60000,
)

export const fetchVariants = async (bcbv: ByCompoundByVariant, cName: string): Promise<string[]> =>
    cName ? merge(await cachedRemoteVariants(cName), bcbv[cName] ?? []) : []

function uriEncode(s: string): string {
    return encodeURIComponent(s).replaceAll('%20', '+')
}

export const compoundPath = (cName: CompoundName, del: boolean = false) => {
    requireTrue(!!cName[0], 'invalid compound name')
    // eslint-disable-next-line prefer-const
    let [b, v] = cName.map(uriEncode)
    if (!v) v = '-'
    if (del)
        return b
    return `${b}/${v}`
}

export async function fetchCompoundDetailsOrNull(
    cName: CompoundName,
): Promise<Nullable<CompoundInfo>> {
    requireTrue(!!cName[0], 'invalid compound name')
    const path = compoundPath(cName)
    const response = await fetchJson(`/api/data/compounds/${path}`, CompoundInfoSchema)
    if (response) {
        if (!response.pctActive) response.pctActive = 1.0
    }
    return response
}

export const cachedRemoteBlends = cache(
    async () => await fetchJson('/api/data/blends', BlendNamesListSchema, []),
    () => '',
    60000,
)

export const fetchBlends = async (local: string[]): Promise<string[]> =>
    merge(await cachedRemoteBlends(), local)

export async function fetchBlendDetailsOrNull(bName: string): Promise<Nullable<BlendEntry>> {
    requireTrue(!!bName, 'invalid blend name')
    const b = uriEncode(bName)
    const response = await fetchJson(`/api/data/blends/${b}`, BlendEntrySchema)
    return response
}

export const cachedRemoteFrequenciesWithWeights = cache(
    async () => {
        const remote = await fetchJson(
            '/api/data/frequencies.w',
            FrequencyNamesWithWeightsSchema,
            {},
        )
        // sorted from backend then remapped
        return Object.fromEntries(Object.entries(remote).map(([k, v]) => [k, iso8601ToNumber(v)]))
    },
    () => '',
    60000,
)

export const fetchFrequencies = async (local: Record<string, number>): Promise<string[]> => {
    // splice uniquely and sort
    const data = Object.entries({
        ...(await cachedRemoteFrequenciesWithWeights()),
        ...local,
    })
    return data.sort((a, b) => a[1] - b[1]).map((e) => e[0])
}

export async function fetchFrequencyDetailsOrNull(
    fName: string,
): Promise<Nullable<FrequencyEntry>> {
    requireTrue(!!fName, 'invalid frequency name')
    const f = uriEncode(fName)
    const response = await fetchJson(`/api/data/frequencies/${f}`, FrequencyEntrySchema)
    return response
}

export const cachedTransformerNames = cache(
    async () =>
        await fetchJson(
            '/api/data/transformers/names',
            zodNonemptyStringArraySchema('invalid transformer name'),
            [],
        ),
    () => '',
)

export const cachedTransformerFrequencies = cache(
    async () =>
        await fetchJson(
            '/api/data/transformers/frequencies',
            zodNonemptyStringArraySchema('invalid transformer name'),
            [],
        ),
    () => '',
)
