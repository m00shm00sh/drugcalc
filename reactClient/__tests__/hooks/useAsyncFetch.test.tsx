import { act, renderHook, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom'

import { makeInvoker, useAsyncResult } from '../../src/hooks/useAsyncFetch'

describe('useAsyncResult', () => {
    let callCount = 0
    const func = async (arg: number) => {
        ++callCount
        return Promise.resolve(arg)
    }
    it('should rerender on auxdeps change', async () => {
        // use prop to pass auxdep value
        const h = renderHook(({v} : {v: number}) => {
            const inv = makeInvoker({
                func: async () => await func(v),
                initial: 0,
                args: [],
                auxDeps: [v]
            })
            return useAsyncResult(inv)
        }, {initialProps: {v: 1}})
        expect(h.result.current).toBe(0) // initial
        expect(callCount).toBe(1)
        act(() => h.rerender({ v: 2 }))
        await waitFor(() => expect(callCount).toBe(2))
        // we need a rerender with unchanged values for the state value to propagate
        act(() => h.rerender({ v: 2 }))
        expect(callCount).toBe(2)
        expect(h.result.current).not.toBe(0)
    })

    it('should retain stale value on failure', async () => {
        // use prop to pass auxdep value
        const h = renderHook(({v} : {v: number}) => {
            const inv = makeInvoker({
                func: async () => Promise.reject(''),
                initial: 0,
                args: [],
                auxDeps: [v]
            })
            return useAsyncResult(inv)
        }, {initialProps: {v: 1}})
        expect(h.result.current).toBe(0) // initial
        act(() => h.rerender({ v: 2 }))
        // we need a rerender with unchanged values for the state value to propagate
        act(() => h.rerender({ v: 2 }))
        expect(h.result.current).toBe(0)
    })
})