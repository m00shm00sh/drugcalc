import { z } from 'zod'

const GROUP_AUTO_NEW = -1
const GROUP_COPY_PREVIOUS = -2

/** Whether [group] is the auto-new sentinel. */
export const isAutoNewGroup = (group: string) =>
    group === String(GROUP_AUTO_NEW)

/**
 * Group select options: display label -> submitted value. Integer-like keys enumerate first,
 * so numeric groups are listed before the sentinel labels.
 */
export const groupOptionValues = (numRows: number): Record<string, string> => ({
    new: String(GROUP_AUTO_NEW),
    copy: String(GROUP_COPY_PREVIOUS),
    ...Object.fromEntries(Array.from({ length: numRows }, (_, k) => [`${k}`, `${k}`])),
})

/** A group sentinel or a nonnegative integer, in string form. */
export const zodGroupIdString = z
    .string()
    .regex(
        new RegExp(`^(${GROUP_AUTO_NEW}|${GROUP_COPY_PREVIOUS}|0|[1-9][0-9]*)$`),
        'invalid group',
    )

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
