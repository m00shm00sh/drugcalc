import { z } from 'zod'
import type { Nullable } from '../util/util'

export function zodNonemptyStringSchema(): z.ZodString
export function zodNonemptyStringSchema(errMsg: string): z.ZodString
export function zodNonemptyStringSchema(errMsg: Nullable<string>): z.ZodString
export function zodNonemptyStringSchema(
    errMsg?: Nullable<string>,
): z.ZodString {
    return z.string().nonempty(errMsg)
}

export function zodNonemptyStringArraySchema(): z.ZodReadonly<
    z.ZodArray<z.ZodString>
>
export function zodNonemptyStringArraySchema(
    strErr: string,
): z.ZodReadonly<z.ZodArray<z.ZodString>>
export function zodNonemptyStringArraySchema(
    strErr?: Nullable<string>,
): z.ZodReadonly<z.ZodArray<z.ZodString>> {
    return z.readonly(z.array(zodNonemptyStringSchema(strErr)))
}
