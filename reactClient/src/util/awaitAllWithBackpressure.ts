import pMap from 'p-map'

export type AsyncThunk<T> = () => Promise<T>

/** Wrap a value in a thunk so it can be mixed with real async work. */
export function toThunk<T>(o: T): AsyncThunk<T> {
    return () => Promise.resolve(o)
}

/**
 * Run async thunks with a bounded number of in-flight operations.
 *
 * Thunks (rather than already-started promises) are required so that the
 * concurrency limit applies to the work itself, not just to awaiting it.
 */
export const awaitAllWithBackpressure = async <
    TA extends ReadonlyArray<AsyncThunk<unknown>>,
    R = { [K in keyof TA]: Awaited<ReturnType<TA[K]>> },
>(
    thunks: readonly [...TA],
    concurrencyLimit: number = 2,
): Promise<R> => {
    return (await pMap(thunks, (thunk) => thunk(), { concurrency: concurrencyLimit })) as R
}
