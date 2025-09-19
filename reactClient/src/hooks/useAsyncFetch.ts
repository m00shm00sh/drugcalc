import { useEffect, useState } from 'react'

/**
 * Use result of async function in a non-async component.
 *
 * If using a curried function, you may want to supply the original function and curried arg(s) in auxDeps
 * so that useEffect doesn't cause infinite re-rendering. Otherwise, the supplied function will be part of
 * the dependencies.
 */
export default function useAsyncResult<R, T extends unknown[]>(
    func: (...args: [...T]) => Promise<R>,
    args: readonly [...T],
    initial: R,
    auxDeps?: unknown[],
): R {
    const [result, setResult] = useState<R>(initial)
    const deps = [...args, ...(auxDeps !== undefined ? auxDeps : [func])]
    useEffect(() => {
        const fetchResult = async () => {
            const result = await func(...args)
            setResult(result)
        }
        fetchResult()
        return () => {}
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps)
    return result
}
