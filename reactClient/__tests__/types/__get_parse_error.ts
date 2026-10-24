import { z } from 'zod'
import type { Nullable } from '../../src/util/util'

export const parseError = <T extends object, ZT extends z.ZodType<T> = z.ZodType<T>>(
    o: object, schema: ZT
): Nullable<z.core.$ZodErrorTree<z.infer<ZT>>> => {
    const e = schema.safeParse(o).error
    if (e === undefined)
        return undefined
    return z.treeifyError(e)
}
