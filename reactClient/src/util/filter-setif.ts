declare global {
    // TODO: can we constrain these extends to <T extends string | number | boolean | undefined>?
    interface Array<T> {
        /** Filter out duplicates. Caution is necessary when using non-primitive types. */
        filterDistinct(): T[]
        /** Extract intersection of values. Caution is necessary when using non-primitive types. */
        filterIntersecting(other: readonly T[]): T[]
    }
}
function filterDistinct<T>(this: readonly T[]): T[] {
    return this.filter((v, i, a) => a.indexOf(v) === i)
}
function filterIntersecting<T>(this: readonly T[], other: readonly T[]): T[] {
    return this.filter((e) => other.includes(e))
}
Array.prototype.filterDistinct = filterDistinct
Array.prototype.filterIntersecting = filterIntersecting

export const fieldsFilterer = (keys: readonly string[]) =>
    <T extends object>(obj: Partial<T>) => filterFields(obj, keys)

const filterFields = <T extends object>(obj: Partial<T>, keys: readonly string[]) =>
    Object.fromEntries(
        Object.entries(obj)
            .filter(([k,]) => keys.indexOf(k) >= 0)
    ) as Partial<T>

export type ValueOrSupplier<T> = T extends (...args: unknown[]) => unknown ? never : T | (() => T)

// set obj.key if value is truthy; this avoids having to tell apart missing key from key with undefined value
export function setIf<T extends object, K extends keyof T, V extends T[K]>(
    obj: T,
    key: K,
    value: V
) {
    if (value) {
        const o_ = obj as Record<keyof T, V>
        o_[key] = value
    }
}

export function stripIf<T extends object>(pred: boolean, obj: T, key: string) {
    if (pred && key in obj) {
        delete (obj as Record<string, unknown>)[key]
    }
}

export function getOrElse<T, V>(
    obj: T extends object ? T : never,
    key: string,
    orElse: ValueOrSupplier<V>,
): V {
    const objV = key in obj ? (obj as Record<string, V>)[key] : undefined
    if (objV) return objV
    if (typeof orElse === 'function') return orElse()
    return orElse as V
}
