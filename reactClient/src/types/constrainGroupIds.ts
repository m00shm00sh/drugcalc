import type { z } from 'zod'

export const GROUP_AUTO_NEW = -1
export const GROUP_COPY_PREVIOUS = -2

const isValidGroupId = (groupId: number, numRows: number) =>
    groupId === GROUP_AUTO_NEW ||
    groupId === GROUP_COPY_PREVIOUS ||
    (Number.isInteger(groupId) && groupId >= 0 && groupId < numRows)

export const zodSuperRefinerForGroupIds =
    <K extends object>(
        getName: (item: K) => string,
        createError: (item: K) => [path: string, message: string][],
    ) =>
        <T extends K[]>(data: T, ctx: z.core.$RefinementCtx) => {
            for (const [index, datum] of data.entries()) {
                if (!isValidGroupId(Number(getName(datum)), data.length))
                    createError(datum).forEach((e) => {
                        ctx.addIssue({
                            code: 'custom',
                            path: [index, e[0]],
                            message: e[1],
                        })
                    })
            }
        }
