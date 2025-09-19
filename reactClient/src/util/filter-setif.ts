export function filterOutIndices<T>(items: T[], indices: number[]): T[] {
    return items.filter((_, i) => !indices.includes(i))
}

declare global {
    interface Array<T> {
        filterDistinct(): T[];
    }
}
function filterDistinct<T>(this: T[]): T[] {
    return this.filter((v, i, a) => a.indexOf(v) === i)
}
Array.prototype.filterDistinct = filterDistinct

// set o.k if value is truthy; this avoids having to tell apart missing key from key with undefined value
export function setIf<T, K extends keyof T, V extends T[K]>(
    obj: T,
    key: K,
    value: V,
) {
    if (value) {
        const o_ = obj as Record<keyof T, V>
        o_[key] = value
    }
}
