import { describe, expect, it } from '@jest/globals'
import cache from '../../src/util/cache'
import type { Invalidatable } from '../../src/util/cache'

describe('cache', () => {
    const cachers: Invalidatable[] = []

    afterAll(() => {
        cachers.forEach((m) => { m.invalidateAll() })
    })

    const nextC = <R, T extends unknown[]>(
        func: (...args: [...T]) => Promise<R>,
        keyHasher: (...args: [...T]) => string,
        writeTimeoutMsec: number | undefined = undefined,
    ) => {
        const next = writeTimeoutMsec !== undefined
            ? cache(func, keyHasher, writeTimeoutMsec)
            : cache(func, keyHasher)
        cachers.push(next)
        return next
    }

    let invokeCount = 0
    const testFunc0 = async () => { ++invokeCount }
    const failFunc = async () => { ++invokeCount; throw new Error('fail') }
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const testFunc1 = async (_: object) => { ++invokeCount }


    it('should cache success', async () => {
        invokeCount = 0
        const m = nextC(testFunc0, () => '', 1000)
        await m()
        await m()
        expect(invokeCount).toBe(1)
    })

    it('should cache failure', async () => {
        invokeCount = 0
        const m = nextC(failFunc, () => '', 1000)
        // eslint-disable-next-line no-empty
        try { await m() } catch { }
        // eslint-disable-next-line no-empty
        try { await m() } catch { }
        expect(invokeCount).toBe(1)
    })

    it('should invalidate on timeout', async () => {
        invokeCount = 0
        const m = nextC(testFunc0, () => '', 50)
        await m()
        await new Promise(r => setTimeout(r, 100))
        await m()
        expect(invokeCount).toBe(2)
    })

    it('should invalidate on invalidate', async () => {
        invokeCount = 0
        const m = nextC(testFunc0, () => '', 1000)
        await m()
        m.invalidateAll()
        await m()
        expect(invokeCount).toBe(2)
    })

    it('should hash stably', async () => {
        invokeCount = 0
        const m = nextC(testFunc1, JSON.stringify, 1000)
        await m({})
        await m({})
        expect(invokeCount).toBe(1)
    })
    it('should default timeout to max number', () => {
        const m = nextC(testFunc0, () => '')
        expect(m.writeExpireAfterMsec).toBe(Number.MAX_VALUE)
    })
})
