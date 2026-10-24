import type { FieldValues, Path } from 'react-hook-form'

export const getSelectedIndices =
    <T extends FieldValues>(
        watcher: (e: Path<T>) => {'selected': boolean}[],
        expr: Path<T>,
    ): number[] =>
        (watcher(expr) ?? [])
            .map((v, i) => [i, v.selected])
            .filter((e) => e[1])
            .map((e) => e[0] as number)
