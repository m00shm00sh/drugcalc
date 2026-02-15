import { describe, expect, it } from '@jest/globals'
import { z } from 'zod'
import {
    mockNextFetchFail,
    mockNextFetch404,
    mockNextFetchHardFail,
    mockNextFetch,
    checkHeaders,
    checkParams,
    resetMock
} from '../__fetch_utils'

import { del, fetchJson, NO_RESPONSE, postJson } from '../../src/util/fetcher'

describe('fetchJson', () => {
    const zStoN = z.record(z.string(), z.number())
    it('should accept json', async () => {
        mockNextFetch404()
        await fetchJson('', zStoN)
        checkHeaders({Accept: 'application/json'})
    })
    it('should throw error on non-404 failed request', async () => {
        mockNextFetchFail(499)
        await expect(() => fetchJson('', zStoN)).rejects.toThrow('couldn\'t fetch')
    })
    it('should return object on 404', async () => {
        mockNextFetch404()
        const o = await fetchJson('', zStoN, {'a': 1})
        expect(o).toEqual({'a': 1})
    })
    it('should return invocation result on 404', async () => {
        mockNextFetch404()
        const o = await fetchJson('', zStoN, () => ({'a': 2}))
        expect(o).toEqual({'a': 2})
    })
    it('should return object on server fail', async () => {
        mockNextFetchHardFail('a')
        const o = await fetchJson('', zStoN, {'a': 1})
        expect(o).toEqual({'a': 1})
    })
    it('should reject invalid object', async () => {
        mockNextFetch([])
        await expect(() => fetchJson('', zStoN)).rejects.toThrow('invalid_type')
    })
    it('should return valid object', async () => {
        const io = { 'a': 1}
        mockNextFetch(io)
        const oo = await fetchJson('', zStoN)
        expect(oo).toEqual(io)
    })
})

describe('postJson',  () => {
    it('should use POST method', async () => {
        mockNextFetch({})
        await postJson('', NO_RESPONSE, {})
        checkParams('method', (p) => expect(p).toBe('POST'))
    })
    it('should send and accept json', async () => {
        mockNextFetch({})
        await postJson('', NO_RESPONSE, {})
        checkHeaders({
            accept: 'application/json',
            'content-type': 'application/json'
        })
    })
    it('should send auxiliary headers', async () => {
        mockNextFetch({})
        const auxHdrs = {
            'Hello': 'world ! ',
            'foo': 'bar fred'
        }
        await postJson('', NO_RESPONSE, {}, auxHdrs)
        checkHeaders(auxHdrs)
    })
    it('should error on 404', async () => {
        mockNextFetch404()
        await expect(() => postJson('', NO_RESPONSE, {})).rejects.toThrow('couldn\'t post')
    })
    it('should return undefined when body schema is NO_RESPONSE', async () => {
        mockNextFetch([1])
        await expect(() => postJson('', NO_RESPONSE, {})).resolves.toBeUndefined()
    })
    const zStoN = z.record(z.string(), z.number())
    it('should reject invalid object', async () => {
        mockNextFetch([])
        await expect(() => postJson('', zStoN, {})).rejects.toThrow('invalid_type')
    })
    it('should return valid object', async () => {
        const io = { 'a': 1}
        mockNextFetch(io)
        const oo = await postJson('', zStoN, {})
        expect(oo).toEqual(io)
    })
})

describe('del', () => {
    /* TODO: mocked fetch is sensitive to stale mock value here (and only here) and this reset is necessary;
     *       figure out why
     */
    beforeEach(resetMock)

    it('should error if no bearer', async () => {
        mockNextFetch404()
        await expect(() => del('', '')).rejects.toThrow('missing bearer')
    })
    it('should use DELETE method', async () => {
        mockNextFetch({})
        await del('', 'a')
        checkParams('method', (p) => expect(p).toBe('DELETE'))
    })
    it('should set Authorization: Bearer header', async () => {
        mockNextFetch({})
        await del('', 'aa bb')
        checkHeaders({Authorization: 'Bearer aa bb'})
    })
    it('should error on non-404 4xx', async () => {
        mockNextFetchFail(499)
        await expect(() => del('', 'a')).rejects.toThrow('couldn\'t delete')
    })
    it('should return false on 404', async () => {
        mockNextFetch404()
        expect(await del('', 'a')).toBe(false)
    })
    it('should return true on success', async () => {
        mockNextFetch({})
        expect(await del('', 'a')).toBe(true)
    })
})
