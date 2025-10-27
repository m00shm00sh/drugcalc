
export type Invalidatable = {
    invalidateAll: () => undefined
}
type InvalidatableAsyncFunction<R, T extends unknown[]> =
    Invalidatable & ((...args: [...T]) => Promise<R>)

export default function cache<R, T extends unknown[]>(
    func: (...args: [...T]) => Promise<R>,
    keyHasher: (...args: [...T]) => string,
    timeoutMsec: number = Number.MAX_VALUE,
): InvalidatableAsyncFunction<R, T> {
    type CacheEntry = {
        inserted: number
        future: Promise<R>
        invalidatorID: number | NodeJS.Timeout
    }
    const resultCache = new Map<string, CacheEntry>()

    const callable: InvalidatableAsyncFunction<R, T> = async (...args: [...T]): Promise<R> => {
        const hashedK = keyHasher(...args)
        const e = resultCache.get(hashedK)
        const now = Date.now()
        if (e === undefined || now - e.inserted > timeoutMsec) {
            const fut = func(...args)
            resultCache.set(hashedK, {
                future: fut,
                inserted: now,
                invalidatorID: setTimeout(
                    (k: string) => {
                        const now = Date.now()
                        const e = resultCache.get(k)
                        if (e === undefined || now - e.inserted < timeoutMsec) return
                        resultCache.delete(hashedK)
                    },
                    timeoutMsec,
                    hashedK,
                )
            })
            return await fut
        }

        return await e.future
    }

    callable.invalidateAll = () => {
        resultCache.forEach((v) => {
            clearTimeout(v.invalidatorID)
        })
        resultCache.clear()
    }
    return callable
}
