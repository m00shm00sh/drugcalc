import { type Nullable, require } from './util'
import { BACKEND } from './constants'
import type { BlendEntry } from '../types/Blends'
import type {
    ByCompoundByVariant,
    CompoundName,
    CompoundInfo,
} from '../types/Compounds'
import { type FrequencyEntry } from '../types/Frequencies'
import memoize from './memoize'

async function fetchJson<T extends object>(
    relativeLink: string,
    on404: T | (() => T),
): Promise<T>;
async function fetchJson<T extends object>(
    relativeLink: string,
): Promise<Nullable<T>>;

async function fetchJson<T extends object>(
    relativeLink: string,
    on404?: T | (() => T),
): Promise<Nullable<T>> {
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
        return on404
    }
    const data = await response.json()
    return data
}


export function compoundsFetcher(bcbv: ByCompoundByVariant): () => Promise<string[]> {
    return async function (): Promise<string[]> {
        const data: string[] = assertNotNull(await fetchJson<string[]>('/api/data/compounds', []))
        const cb = Object.keys(bcbv ?? {})
        data.push(...cb)
        data.sort()
        return data
    }
}

const memoizedRemoteCompounds = memoize(
    async () => await fetchJson<readonly string[]>('/api/data/compounds', []),
    () => '',
    60000,
)
export async function fetchCompounds(
    bcbv: ByCompoundByVariant,
): Promise<string[]> {
    const data = [...(await memoizedRemoteCompounds())]
    const cb = Object.keys(bcbv ?? {})
    data.push(...cb)
    data.sort()
    return data.filterDistinct()
}

// exported for components/BlendsEditor/VariantsFetcherContext
export const memoizedRemoteVariants = memoize(
    async (cName: string) => {
        require(!!cName, 'unexpected empty cName')
        const b = encodeURIComponent(cName).replace('%20', '+')
        return await fetchJson<readonly string[]>(`/api/data/compounds/${b}`, [])
    },
    (s: string) => s,
    60000,
)

export async function fetchVariants(
    bcbv: ByCompoundByVariant,
    cName: string,
): Promise<string[]> {
    if (!cName) return []
    const data = [...(await memoizedRemoteVariants(cName))]
    const cv = bcbv[cName] ?? []
    data.push(...cv)
    data.sort()
    return data.filterDistinct()
}

function uriEncode(s: string): string {
    return encodeURIComponent(s).replaceAll('%20', '+')
}

export async function fetchCompoundDetailsOrNull(
    cName: CompoundName,
): Promise<Nullable<CompoundInfo>> {
    require(!!cName[0], 'invalid compound name')
    let [b, v] = cName.map(uriEncode)
    if (!v) v = '-'
    const response = await fetchJson<CompoundInfo>(
        `/api/data/compounds/${b}/${v}`,
    )
    if (response) {
        if (!response.pctActive) response.pctActive = 1.0
    }
    return response
}

export async function fetchBlendDetailsOrNull(
    bName: string,
): Promise<Nullable<BlendEntry>> {
    require(!!bName, 'invalid blend name')
    const b = uriEncode(bName)
    const response = await fetchJson<BlendEntry>(`/api/data/blends/${b}`)
    return response
}

export async function fetchFrequencyDetailsOrNull(
    fName: string,
): Promise<Nullable<FrequencyEntry>> {
    require(!!fName, 'invalid frequency name')
    const f = uriEncode(fName)
    const response = await fetchJson<FrequencyEntry>(
        `/api/data/frequencies/${f}`,
    )
    return response
}
