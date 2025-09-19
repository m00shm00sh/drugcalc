import pLimit from 'p-limit'
import type {
    ArrayPath,
    FieldArray,
    FieldValues,
    Path,
    UseFieldArrayUpdate,
    UseFormReturn,
} from 'react-hook-form'
import type { Nullable } from './util'
import type { Selectable } from '../types/Selectable'

type RHFKey1<FormContainer> = keyof FormContainer & ArrayPath<FormContainer>
type RHFKey<FormContainer> = RHFKey1<FormContainer> & Path<FormContainer>

export function selectedItemsFromRemoteFormLoader<
    FormContainer extends FieldValues,
    FormRow extends FieldArray<FormContainer, RHFKey1<FormContainer>> &
        Selectable,
>(
    formMethods: UseFormReturn<FormContainer>,
    arrayKey: RHFKey<FormContainer>,
    fetchDetailsOrUndefined: (data: FormRow) => Promise<Nullable<FormRow>>,
    errorKeys: (index: number) => Path<FormContainer>[],
    resetValues: (incoming: FormRow) => FormRow,
    updater: UseFieldArrayUpdate<FormContainer, typeof arrayKey>,
    concurrencyLimit: number = 2,
): () => Promise<void> {
    const limiter = pLimit(concurrencyLimit)

    const loader = async (data: FormRow[]): Promise<Nullable<FormRow>[]> =>
        Promise.all(
            data
                .map((e) => (e.selected ? fetchDetailsOrUndefined(e) : e))
                .map((f) => limiter(() => f)),
        )

    const lastComponent = (s: string) => s.substring(s.lastIndexOf('.') + 1)

    return async () => {
        const { getValues, setError, clearErrors } = formMethods
        const data = getValues(arrayKey)
        const newData = await loader(data)
        for (const [i, d] of newData.entries()) {
            if (!d?.selected) continue
            if (d === undefined) {
                for (const ek of errorKeys(i)) {
                    setError(ek, {
                        message: `couldn't fetch ${lastComponent(ek)}`,
                    })
                    updater(i, resetValues(data[i]))
                }
            } else {
                for (const ek of errorKeys(i)) {
                    clearErrors(ek)
                }
                d.selected = false
                updater(i, d)
            }
        }
    }
}
