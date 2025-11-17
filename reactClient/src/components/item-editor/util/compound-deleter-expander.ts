import type { FieldPath, UseFieldArrayReturn, UseFormReturn } from 'react-hook-form'

import type { CompoundDeleterDataContainer, CompoundDeleterRow } from '../../../types/Compounds'
import { awaitAllWithBackpressure, toAwaitable } from '../../../util/awaitAllWithBackpressure'
import { cachedRemoteVariantsOrNull } from '../../../util/data-fetcher'
import type { Nullable } from '../../../util/util'

export function expandExpansionItems(
    formMethods: UseFormReturn<CompoundDeleterDataContainer>,
    arrayMethods: UseFieldArrayReturn<CompoundDeleterDataContainer, 'compounds'>,
    concurrencyLimit: number = 2,
) : () => Promise<void> {

    const loader = async (data: CompoundDeleterRow[]): Promise<Nullable<string[]>[]> =>
        awaitAllWithBackpressure(
            data.map((e) => (e.expand
                ? (e.vx !== undefined
                    ? toAwaitable(e.vx)
                    : cachedRemoteVariantsOrNull(e.compound))
                : toAwaitable([]))
            ),
            concurrencyLimit
        )

    return async () => {
        const { getValues, setError, clearErrors } = formMethods
        const { update, insert } = arrayMethods
        const data = getValues('compounds')
        const newData = await loader(data)
        // use reverse order so we don't have to recompute indices
        for (const [i, d] of newData.reverse().entries()) {
            if (!data[i]?.expand) continue
            if (d === undefined) {
                setError(`compounds.${i}.compound`, {
                    message: `couldn't fetch variants`,
                })
                continue
            }
            const [v0, ...vRest] = d
            update(i, { compound: data[i].compound, selected: true, ...(v0 && { variant: v0 })})
            if (vRest.length > 0) {
                const add = vRest.map((v) => ({ compound: data[i].compound, variant: v, selected: true }))
                insert(i + 1, add)
            }
            const toClear = Array(d.length)
                .fill('')
                .map((_, ai) => `compounds.${i + ai}` as FieldPath<CompoundDeleterDataContainer>)
            clearErrors(toClear)

        }
    }
}
