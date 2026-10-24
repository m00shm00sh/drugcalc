function doThrow(message: string): never {
    throw Error(message)
}

export function requireNotNull<T>(
    arg: T | null | undefined,
    message: string = 'null check failed'
): T {
    return arg ?? doThrow(message)
}

// console.assert() is only useful for logging not throwing so we have this
export function requireTrue(expr: boolean, message: string = 'requirement failed'): void {
    if (!expr) doThrow(message)
}

export type Nullable<T> = T | undefined

export function assertNotNull<T>(arg: Nullable<T>): T {
    return arg as T
}
