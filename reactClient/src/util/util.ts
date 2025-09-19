export function doThrow(message: string): never {
    throw Error(message)
}

export function requireNotNull<T>(
    arg: T | null | undefined,
    message: string = '',
): T {
    return arg ?? doThrow(message ?? 'null check failed')
}

// console.assert() is only useful for logging not throwing so we have this
export function require(expr: boolean, message: string = ''): void {
    if (!expr) doThrow(message)
}

export type Nullable<T> = T | undefined;

export function assertNotNull<T>(arg: Nullable<T>): T {
    return arg as T
}
