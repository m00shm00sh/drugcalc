import pLimit from 'p-limit'
import type {
    ArrayPath,
    FieldArray,
    FieldValues,
    Path,
    UseFieldArrayUpdate,
    UseFormReturn,
} from 'react-hook-form'
import type { Selectable } from '../types/Selectable'
import { require, type Nullable } from './util'
import type { SetStateAction } from 'react'
import { NO_RESPONSE, postJson } from './fetcher'

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
            if (!data[i]?.selected) continue
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

export function selectedItemsToRemoteSender<
    FormContainer extends FieldValues,
    Row extends object & Selectable,
    V extends object,
    RequestBody extends Record<string, V>,
>(
    formMethods: UseFormReturn<FormContainer>,
    arrayKey: RHFKey<FormContainer>,
    mapEncoder: (rows: Row[]) => RequestBody,
    postEndpoint: string,
    setLocalStorage: (map: SetStateAction<RequestBody>) => void,
    toRemote: boolean,
    auth: Nullable<string>,
): (rows: Row[]) => Promise<void> {
    return async (rows: Row[]) => {
        const { setError } = formMethods
        const selectedRows = rows.filter((e) => e.selected)
        const map = mapEncoder(selectedRows)
        if (toRemote) {
            require(!!auth, "specify auth token")
            try {
                await postJson(postEndpoint, NO_RESPONSE, map, {
                    'Authorization': `Bearer ${auth}`
                })
            } catch (e) {
                if (e instanceof Error)
                    setError(arrayKey, { message: e.message })
            }
        } else setLocalStorage(map)
    }
}
