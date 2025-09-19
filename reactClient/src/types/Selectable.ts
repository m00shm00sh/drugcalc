import type { Path, FieldValues } from 'react-hook-form'
import { z } from 'zod'

export const SelectableSchema = z.object({
    selected: z.literal([true, false]).optional(),
})
export type Selectable = z.infer<typeof SelectableSchema>

export function getSelectedIndices<T extends FieldValues>(
    watcher: (e: Path<T>) => Selectable[],
    expr: Path<T>,
): number[] {
    return watcher(expr)
        .map((v, i) => [i, v.selected])
        .filter((e) => e[1])
        .map((e) => e[0] as number)
}
