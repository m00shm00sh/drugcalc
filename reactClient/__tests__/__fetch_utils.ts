import fetchMock from 'jest-fetch-mock'
import type { Invalidatable } from '../src/util/cache'
import type { Nullable } from '../src/util/util'

export const resetMock = () => fetchMock.resetMocks()

export const mockNextFetch = (o?: object) => {
    if (o === undefined)
        throw Error('unexpected undefined')
    fetchMock.mockOnce(JSON.stringify(o))
}

// mock fetch failing with error instead of soft fail with non-200 response
export const mockNextFetchHardFail = (message: string) => {
    fetchMock.mockReject(new Error(message))
}

export const mockNextFetchFail = (status: number) => {
    fetchMock.mockOnce(async () => ({ status }))
}
export const mockNextFetch404 = () =>
    mockNextFetchFail(404)

export const checkCorrectEndpoint = (ep: readonly string[]) => {
    const lastArg = fetchMock.mock.lastCall?.[0]
    expect(typeof lastArg).toBe('string')
    const toks = (lastArg as string).split('/')
    const apiDatIdx = toks.indexOf('api')
    if (apiDatIdx < 0 || toks[apiDatIdx + 1] !== 'data')
        throw Error('could not parse fetch uri')
    const el = ep.length
    expect(toks.slice(apiDatIdx + 2, apiDatIdx + 2 + el)).toEqual(ep)
}

export const lastCallPathItems = (lastN: number = 1): string[] => {
    const lastArg = fetchMock.mock.lastCall?.[0]
    expect(typeof lastArg).toBe('string')
    const toks = (lastArg as string).split('/')
    return toks.slice(-lastN)
}

export const lastCallParams = () =>
    fetchMock.mock.lastCall?.[1] ?? {}

export const checkHeaders = (headers: Record<string, string>) =>
    checkParams('headers', (p: unknown) => {
        expect(p).not.toBeUndefined()
        // if this fails, our mock was invoked where it shouldn't've been
        expect(p).not.toBeInstanceOf(Headers)
        const reqHdrs = p as Record<string, string>
        for (const [k, checkV] of Object.entries(headers)) {
            // we need to do a case-insensitive lookup, which entails a linear scan of all keys
            let hv: Nullable<string>
            for (const [rk, rv] of Object.entries(reqHdrs)) {
                if (rk.toLowerCase() === k.toLowerCase()) {
                    hv = rv
                    break
                }
            }
            expect(hv).toEqual(checkV)
        }
    })

export const checkParams = (p: keyof RequestInit, checker: (p: unknown) => void) => {
    const pv = lastCallParams()[p]
    expect(pv).not.toBe(undefined)
    checker(pv)
}

export const mockCacheableFetch = async <T extends object, R>(
    cache: Invalidatable,
    invoke: () => Promise<Nullable<R>>,
    o: T
) => {
    cache.invalidateAll()
    return await mockUncachedFetch(invoke, o)
}

// XXX: jest reports this is as dead code
export const mockCacheableFetch404 = async <T>(
    cache: Invalidatable,
    invoke: () => Promise<Nullable<T>>,
) => {
    cache.invalidateAll()
    mockNextFetch404()
    return await invoke()
}

export const mockUncachedFetch = async <T extends object, R>(
    invoke: () => Promise<Nullable<R>>,
    o?: T
) => {
    if (o !== undefined)
        mockNextFetch(o)
    else // mock 404 to skip parsing body to prevent false failure
        mockNextFetch404()
    return await invoke()
}

export const checkCacheableFetch = async <T extends object, R>(
    cache: Invalidatable,
    invoke: () => Promise<Nullable<T | R>>, o: T, o2?: T | R
) => {
    const r = mockCacheableFetch(cache, invoke, o)
    o2 ??= o
    await expect(r).resolves.toEqual(o2)
}

export const checkCacheableFetch404 = async <T extends object>(
    cache: Invalidatable,
    invoke: () => Promise<Nullable<T>>,
    o?: T
) => {
    cache.invalidateAll()
    mockNextFetch404()
    const invocation = expect(invoke).resolves
    if (o !== undefined)
        await invocation.toEqual(o)
    else
        await invocation.toBeUndefined()
}

export const checkUncachedFetch = async <T extends object, R>(
    invoke: () => Promise<Nullable<T | R>>, o: T, o2?: T | R
) => {
    mockNextFetch(o)
    o2 ??= o
    await expect(invoke).resolves.toEqual(o2)
}

export const encodePathParameter = (p: string): string => encodeURIComponent(p).replaceAll('%20', '+')
