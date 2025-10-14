export function filterOutIndices<T>(items: T[], indices: number[]): T[] {
    return items.filter((_, i) => !indices.includes(i))
}

declare global {
    interface Array<T> {
        filterDistinct(): T[]
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

export type ValueOrSupplier<T> = T extends (...args: unknown[]) => unknown ? never : T | (() => T)

// set obj.key if value is truthy; this avoids having to tell apart missing key from key with undefined value
export function setIf<T, K extends keyof T, V extends T[K]>(obj: T, key: K, value: V | (() => V)) {
    if (value) {
        const o_ = obj as Record<keyof T, V>
        if (typeof value === 'function') o_[key] = (value as () => V)()
        else o_[key] = value
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
