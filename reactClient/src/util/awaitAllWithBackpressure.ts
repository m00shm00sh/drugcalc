import pMap from 'p-map'

export const toAwaitable = <T>(o: T): Promise<T> => (async () => await o)()

export const awaitAllWithBackpressure = async <
    TA extends ReadonlyArray<Promise<unknown>>,
    R = {[K in keyof TA]: Awaited<TA[K]>}
>(
    awaitables: readonly [...TA],
    concurrencyLimit: number = 2
): Promise<R> => {
    return await pMap(awaitables, e => e, { concurrency: concurrencyLimit }) as R
}