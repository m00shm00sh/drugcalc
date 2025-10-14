import { useEffect, useState } from 'react'

type InvokerArgs<R, T extends unknown[], U extends unknown[]> = {
    func: (...args: readonly [...T]) => Promise<R>
    args: readonly [...T]
    initial: R
    auxDeps?: [...U]
}

type _Invokable<R> = {
    func: (...args: readonly unknown[]) => Promise<R>
    args: readonly unknown[]
    initial: R
    auxDeps: readonly unknown[]
}

/* it's easier to get typescript to coherently handle the types with a function wrapper instead of
 * yoloing the objects so i'm keeping it in here
 */
export function makeInvoker<R, T extends unknown[], U extends unknown[]>(
    args: InvokerArgs<R, T, U>,
): _Invokable<R> {
    return {
        func: args.func as (...args: readonly unknown[]) => Promise<R>,
        args: args.args,
        initial: args.initial,
        auxDeps: args.auxDeps as readonly unknown[],
    }
}

/**
 * Use result of async function in a non-async component.
 *
 * If using a curried function, you may want to supply the original function and curried arg(s) in auxDeps
 * so that useEffect doesn't cause infinite re-rendering. Otherwise, the supplied function will be part of
 * the dependencies.
 */
export function useAsyncResult<R>(invocation: _Invokable<R>): R {
    const [result, setResult] = useState<R>(invocation.initial)
    const deps = [
        ...invocation.args,
        ...(invocation.auxDeps !== undefined ? invocation.auxDeps : [invocation.func]),
    ]
    // biome-ignore lint/correctness/useExhaustiveDependencies: see three lines above
    useEffect(() => {
        const fetchResult = async () => {
            const result = await invocation.func(...invocation.args)
            setResult(result)
        }
        fetchResult()
        return () => {}
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [...deps])
    return result
}
