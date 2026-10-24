import type { z } from 'zod'

export const zodSuperRefinerForUniqueArray =
    <K extends object, NK>(
        getName: (item: K) => NK,
        createError: (item: K) => [path: string, message: string][],
        post: (names: readonly NK[]) => string = () => '',
        hasMatch: (seen: readonly NK[], cur: NK) => boolean = (s, n) => s.includes(n)
    ) =>
        <T extends K[]>(data: T, ctx: z.core.$RefinementCtx) => {
            const seen: NK[] = []
            for (const [index, datum] of data.entries()) {
                const name = getName(datum)
                if (hasMatch(seen, name)) {
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
