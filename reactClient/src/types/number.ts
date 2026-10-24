import { z } from 'zod'
import type { Nullable } from '../util/util'

export function zodPositiveNumberSchema(): z.ZodNumber
export function zodPositiveNumberSchema(what: string): z.ZodNumber
export function zodPositiveNumberSchema(what: Nullable<string>): z.ZodNumber
export function zodPositiveNumberSchema(what?: Nullable<string>): z.ZodNumber {
    what ??= 'value'
    return z.number({ error: `input a ${what}` }).gt(0, { error: `${what} too low` })
}
