import { z } from 'zod'
import {
    BlendEntrySchema,
    BlendNamesListSchema,
    type BlendEntry,
} from '../types/Blends'
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
import { BACKEND } from './constants'
import type { ValueOrSupplier } from './filter-setif'
import memoize from './memoize'
import { require, type Nullable } from './util'


async function fetchJson<
    T extends object,
    ZT extends z.ZodType<T> = z.ZodType<T>,
>(
    relativeLink: string,
    schema: ZT,
    on404: ValueOrSupplier<z.infer<typeof schema>>,
): Promise<z.infer<typeof schema>>

async function fetchJson<
    T extends object,
    ZT extends z.ZodType<T> = z.ZodType<T>,
>(relativeLink: string, schema: ZT): Promise<Nullable<z.infer<typeof schema>>>

async function fetchJson<
    T extends object,
    ZT extends z.ZodType<T> = z.ZodType<T>,
>(
    relativeLink: string,
    schema: ZT,
    on404?: ValueOrSupplier<z.infer<typeof schema>>,
): Promise<Nullable<z.infer<typeof schema>>> {
    const response = await fetch(`${BACKEND}${relativeLink}`, {
        headers: {
            Accept: 'application/json',
        },
    })
    if (!response.ok && response.status !== 404)
        throw Error(`couldn't fetch ${relativeLink}: ${response.status}`)
    if (response.status === 404) {
        if (on404 === undefined) return undefined
        if (typeof on404 === 'function') return on404()
        return on404 as z.infer<typeof schema>
    }
    const data = await response.json()
    return schema.parse(data)
}

export const NO_RESPONSE = z.undefined()

export async function postJson<
    R,
    T extends object,
    ZT extends z.ZodType<R> = z.ZodType<R>,
>(
    relativeLink: string,
    responseSchema: ZT,
    body: T,
    auxHeaders: Record<string, string> = {},
): Promise<z.infer<typeof responseSchema>> {
    const response = await fetch(`${BACKEND}${relativeLink}`, {
        method: 'POST',
        headers: {
            Accept: 'application/json',
            'Content-type': 'application/json',
            ...auxHeaders,
        },
        body: JSON.stringify(body),
    })
    if (!response.ok) {
        const text = await response.text()
        throw Error(
            `couldn't post ${relativeLink}: ${text}`,
        )
    }
    if (<unknown>responseSchema === NO_RESPONSE) {
        await response.text()
        return undefined as z.infer<typeof responseSchema>
    }

    const data = await response.json()
    return responseSchema.parse(data)
}

function merge<T>(
    remote: readonly T[],
    local: readonly T[],
    sortBy?: (a: T, b: T) => number,
): T[] {
    const data = [...remote, ...local]
    data.sort(sortBy)
    return data.filterDistinct()
}

export const memoizedRemoteCompounds = memoize(
    async () =>
        await fetchJson('/api/data/compounds', CompoundNamesListSchema, []),
    () => '',
    60000,
)
export const fetchCompounds = async (
    bcbv: ByCompoundByVariant,
): Promise<string[]> =>
    merge(await memoizedRemoteCompounds(), Object.keys(bcbv ?? {}))

// exported for components/BlendsEditor/VariantsFetcherContext
export const memoizedRemoteVariants = memoize(
    async (cName: string) => {
        require(!!cName, 'unexpected empty cName')
        const b = encodeURIComponent(cName).replace('%20', '+')
        return await fetchJson(
            `/api/data/compounds/${b}`,
            VariantNamesListSchema,
            [],
        )
    },
    (s: string) => s,
    60000,
)

export const fetchVariants = async (
    bcbv: ByCompoundByVariant,
    cName: string,
): Promise<string[]> =>
    cName ? merge(await memoizedRemoteVariants(cName), bcbv[cName] ?? []) : []

function uriEncode(s: string): string {
    return encodeURIComponent(s).replaceAll('%20', '+')
}

export async function fetchCompoundDetailsOrNull(
    cName: CompoundName,
): Promise<Nullable<CompoundInfo>> {
    require(!!cName[0], 'invalid compound name')
    // eslint-disable-next-line prefer-const
    let [b, v] = cName.map(uriEncode)
    if (!v) v = '-'
    const response = await fetchJson(
        `/api/data/compounds/${b}/${v}`,
        CompoundInfoSchema,
    )
    if (response) {
        if (!response.pctActive) response.pctActive = 1.0
    }
    return response
}

export const memoizedRemoteBlends = memoize(
    async () => await fetchJson('/api/data/blends', BlendNamesListSchema, []),
    () => '',
    60000,
)

export const fetchBlends = async (local: string[]): Promise<string[]> =>
    merge(await memoizedRemoteBlends(), local)

export async function fetchBlendDetailsOrNull(
    bName: string,
): Promise<Nullable<BlendEntry>> {
    require(!!bName, 'invalid blend name')
    const b = uriEncode(bName)
    const response = await fetchJson(`/api/data/blends/${b}`, BlendEntrySchema)
    return response
}

export const memoizedRemoteFrequenciesWithWeights = memoize(
    async () => {
        const remote = await fetchJson(
            '/api/data/frequencies.w',
            FrequencyNamesWithWeightsSchema,
            {},
        )
        // sorted from backend then remapped
        return Object.fromEntries(
            Object.entries(remote).map(([k, v]) => [k, iso8601ToNumber(v)]),
        )
    },
    () => '',
    60000,
)

export const fetchFrequencies = async (
    local: Record<string, number>,
): Promise<string[]> => {
    // splice uniquely and sort
    const data = Object.entries({
        ...(await memoizedRemoteFrequenciesWithWeights()),
        ...local,
    })
    return data.sort((a, b) => a[1] - b[1]).map((e) => e[0])
}

export async function fetchFrequencyDetailsOrNull(
    fName: string,
): Promise<Nullable<FrequencyEntry>> {
    require(!!fName, 'invalid frequency name')
    const f = uriEncode(fName)
    const response = await fetchJson(
        `/api/data/frequencies/${f}`,
        FrequencyEntrySchema,
    )
    return response
}

export const fetchTransformerNames = memoize(
    async () =>
        await fetchJson(
            '/api/data/transformers/names',
            zodNonemptyStringArraySchema('invalid transformer name'),
            [],
        ),
    () => '',
)

export const fetchTransformerFrequencies = memoize(
    async () =>
        await fetchJson(
            '/api/data/transformers/frequencies',
            zodNonemptyStringArraySchema('invalid transformer name'),
            [],
        ),
    () => '',
)
