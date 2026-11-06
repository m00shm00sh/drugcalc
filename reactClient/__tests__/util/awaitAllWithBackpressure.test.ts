import { describe, expect, it } from '@jest/globals'

import { awaitAllWithBackpressure, toThunk } from '../../src/util/awaitAllWithBackpressure'

describe('awaitAllWithBackpressure', () => {
    it('preserves result order', async () => {
        const results = await awaitAllWithBackpressure([
            async () => 1,
            async () => 2,
            async () => 3,
        ])

        expect(results).toEqual([1, 2, 3])
    })

    it('does not start more thunks than the concurrency limit', async () => {
        let active = 0
        let maxActive = 0
        const releases: (() => void)[] = []

        const makeThunk = (value: number) => async () => {
            active++
            maxActive = Math.max(maxActive, active)
            await new Promise<void>((resolve) => releases.push(resolve))
            active--
            return value
        }

        const result = awaitAllWithBackpressure([makeThunk(1), makeThunk(2), makeThunk(3)], 2)
        await new Promise((resolve) => setTimeout(resolve, 0))
        expect(maxActive).toBe(2)

        while (releases.length > 0) {
            releases.shift()?.()
            await new Promise((resolve) => setTimeout(resolve, 0))
        }

        await expect(result).resolves.toEqual([1, 2, 3])
        expect(maxActive).toBe(2)
    })

    it('wraps a plain value with toThunk', async () => {
        await expect(toThunk('value')()).resolves.toBe('value')
    })
})
