import type { Path, FieldValues } from 'react-hook-form'
import { z } from 'zod'

export const zodOptBool = z.literal([true, false]).optional()
export const SelectableSchema = z.object({'selected': zodOptBool})
export type Selectable = z.infer<typeof SelectableSchema>

export const getSelectedIndicesFactory =
    (name: string) =>
        <T extends FieldValues>(
            watcher: (e: Path<T>) => {[name]: boolean}[],
            expr: Path<T>,
        ): number[] =>
            (watcher(expr) ?? [])
                .map((v, i) => [i, v[name]])
                .filter((e) => e[1])
                .map((e) => e[0] as number)

export const getSelectedIndices = getSelectedIndicesFactory('selected')