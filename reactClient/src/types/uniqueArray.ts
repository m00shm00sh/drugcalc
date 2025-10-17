import type { z } from 'zod'

export const zodSuperRefinerForUniqueArray =
    <K extends object>(
        getName: (item: K) => string,
        createError: (item: K) => [string, string][],
        post: (names: readonly string[]) => string = () => '',
    ) =>
        <T extends K[]>(data: T, ctx: z.core.$RefinementCtx) => {
            const seen: string[] = []
            for (const [index, datum] of data.entries()) {
                const name = getName(datum)
                if (seen.includes(name)) {
                    createError(datum).forEach((e) => {
                        ctx.addIssue({
                            code: 'custom',
                            path: [index, e[0]],
                            message: e[1],
                        })
                    })
                } else seen.push(name)
            }
            const p = post(seen)
            if (p) {
                ctx.addIssue({
                    code: 'custom',
                    message: p,
                })
            }
        }
