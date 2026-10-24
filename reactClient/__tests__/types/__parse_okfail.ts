import { expect } from '@jest/globals'

import type { ZodType } from 'zod'

// NOTE: expectParseXxx should return a ()=>void because that's what it(what, f) expects

export const expectParseOk = <T extends object, ZT extends ZodType<T> = ZodType<T>>(
    schema: ZT,
    obj: T
) => (() =>
    expect(() => schema.parse(obj)).not.toThrow()
)

export const expectParseFail = <T extends object, ZT extends ZodType<T> = ZodType<T>>(
    schema: ZT,
    obj: unknown
) => (() =>
    expect(() => schema.parse(obj)).toThrow()
)
