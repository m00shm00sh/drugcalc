import type { z } from 'zod'

export const zodSuperRefinerForGroupIds =
    <K extends object>(
        getName: (item: K) => string,
        createError: (item: K) => [path: string, message: string][],
    ) =>
        <T extends K[]>(data: T, ctx: z.core.$RefinementCtx) => {
            for (const [index, datum] of data.entries()) {
				if (Number(getName(datum)) >= data.length)
                    createError(datum).forEach((e) => {
                        ctx.addIssue({
                            code: 'custom',
                            path: [index, e[0]],
                            message: e[1],
                        })
                    })
            }
        }
