import pLimit from 'p-limit'

export const awaitAllWithBackpressure = async <
    T,
    TA extends (Promise<T> | T)[] = Promise<T>[]
>(
    awaitables: [...TA],
    concurrencyLimit: number = 2
): Promise<T[]> => {
    const limiter = pLimit(concurrencyLimit)
    return await Promise.all(awaitables.map((f) => limiter(() => f)))
}
