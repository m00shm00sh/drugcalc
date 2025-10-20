
export type Invalidatable = {
    invalidateAll: () => undefined
}
type InvalidatableAsyncFunction<R, T extends unknown[]> =
    Invalidatable & ((...args: [...T]) => Promise<R>)

export default function memoize<R, T extends unknown[]>(
    func: (...args: [...T]) => Promise<R>,
    keyHasher: (...args: [...T]) => string,
    timeoutMsec: number = Number.MAX_VALUE,
): InvalidatableAsyncFunction<R, T> {
    type CacheEntry = {
        inserted: number
        future: Promise<R>
    }
    const cache = new Map<string, CacheEntry>()

    const callable: InvalidatableAsyncFunction<R, T> = async (...args: [...T]): Promise<R> => {
        const hashedK = keyHasher(...args)
        const e = cache.get(hashedK)
        const now = Date.now()
        if (e === undefined || now - e.inserted > timeoutMsec) {
            const fut = func(...args)
            cache.set(hashedK, {
                future: fut,
                inserted: now,
            })
            return await fut
        }
        setTimeout(
            (k: string) => {
                const now = Date.now()
                const e = cache.get(k)
                if (e === undefined || now - e.inserted < timeoutMsec) return
                cache.delete(hashedK)
            },
            timeoutMsec,
            hashedK,
        )
        return await e.future
    }

    callable.invalidateAll = () => {
        cache.clear()
    }
    return callable
}
