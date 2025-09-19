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

type RHFKey1<FormContainer> = keyof FormContainer & ArrayPath<FormContainer>;
type RHFKey<FormContainer> = RHFKey1<FormContainer> & Path<FormContainer>;

export function itemsFromRemoteFormLoader<
    FormContainer extends FieldValues,
    FormRow extends FieldArray<FormContainer, RHFKey1<FormContainer>>,
>(
    formMethods: UseFormReturn<FormContainer>,
    arrayKey: RHFKey<FormContainer>,
    itemLoader: (data: FormRow) => Promise<Nullable<FormRow>>,
    itemSkipper: (data: FormRow) => boolean,
    errorKeys: (index: number) => Path<FormContainer>[],
    resetValues: (incoming: FormRow) => FormRow,
    updater: UseFieldArrayUpdate<FormContainer, typeof arrayKey>,
    concurrencyLimit: number = 2,
): () => Promise<void> {
    const limiter = pLimit(concurrencyLimit)

    const loader = async (data: FormRow[]): Promise<Nullable<FormRow>[]> =>
        Promise.all(
            data
                .map((e) => (itemSkipper(e) ? e : itemLoader(e)))
                .map((f) => limiter(() => f)),
        )

    const lastComponent = (s: string) => s.substring(s.lastIndexOf('.') + 1)

    return async () => {
        const { getValues, setError, clearErrors } = formMethods
        const data = getValues(arrayKey)
        const newData = await loader(data)
        for (const [i, d] of newData.entries()) {
            if (d === undefined) {
                for (const ek of errorKeys(i)) {
                    setError(ek, { message: `couldn't fetch ${lastComponent(ek)}` })
                    updater(i, resetValues(data[i]))
                }
            } else {
                for (const ek of errorKeys(i)) {
                    clearErrors(ek)
                }
                updater(i, d)
            }
        }
    }
}
