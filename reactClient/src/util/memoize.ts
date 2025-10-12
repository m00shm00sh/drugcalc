export default function memoize<R, T extends unknown[]>(
    func: (...args: [...T]) => Promise<R>,
    keyHasher: (...args: [...T]) => string,
    timeoutMsec: number = Number.MAX_VALUE,
): (...args: [...T]) => Promise<R> {
    type cacheEntry = {
        inserted: number
        future: Promise<R>
    }
    const cache = new Map<string, cacheEntry>()
    return async (...args: [...T]): Promise<R> => {
        const hashedK = keyHasher(...args)
        const e = cache.get(hashedK)
        const now = Date.now()
        if (e === undefined || now - e.inserted > timeoutMsec) {
            const fut = new Promise<R>((accept, reject) => {
                try {
                    const result = func(...args)
                    accept(result)
                } catch (e) {
                    reject(e)
                }
            })
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
}
