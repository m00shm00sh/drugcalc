import type { SetStateAction } from 'react'
import type {
    ArrayPath,
    FieldArray,
    FieldValues,
    Path,
    UseFieldArrayRemove,
    UseFieldArrayUpdate,
    UseFormReturn,
} from 'react-hook-form'
import type { Selectable } from './Selectable'
import { del, NO_RESPONSE, postJson } from '../../../util/fetcher'
import type { Invalidatable } from '../../../util/cache'
import { type Nullable, requireTrue } from '../../../util/util'
import { awaitAllWithBackpressure, toAwaitable } from '../../../util/awaitAllWithBackpressure'

type RHFKey1<FormContainer> = keyof FormContainer & ArrayPath<FormContainer>
type RHFKey<FormContainer> = RHFKey1<FormContainer> & Path<FormContainer>

export function selectedItemsFromRemoteFormLoader<
    FormContainer extends FieldValues,
    FormRow extends FieldArray<FormContainer, RHFKey1<FormContainer>> & Selectable,
>(
    formMethods: UseFormReturn<FormContainer>,
    arrayKey: RHFKey<FormContainer>,
    fetchDetailsOrUndefined: (data: FormRow) => Promise<Nullable<FormRow>>,
    errorKeys: (index: number) => Path<FormContainer>[],
    resetValues: (incoming: FormRow) => FormRow,
    updater: UseFieldArrayUpdate<FormContainer, typeof arrayKey>,
    concurrencyLimit: number = 2,
): () => Promise<void> {

    const loader = async (data: FormRow[]): Promise<readonly Nullable<FormRow>[]> =>
        awaitAllWithBackpressure(
            data.map((e) => (e.selected ? fetchDetailsOrUndefined(e) : toAwaitable(e))),
            concurrencyLimit
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
    remover: UseFieldArrayRemove,
    arrayKey: RHFKey<FormContainer>,
    mapEncoder: (rows: Row[]) => RequestBody,
    postEndpoint: string,
    setLocalStorage: (map: SetStateAction<RequestBody>) => void,
    toRemote: boolean,
    auth: Nullable<string>,
): (rows: Row[]) => Promise<void> {
    return async (rows: Row[]) => {
        const { setError } = formMethods
        const selectedRows = rows
            .map((e, i) => [e, i] as [Row, number])
            .filter((e) => (!toRemote || e[0].selected))
        const map = mapEncoder(selectedRows.map(e => e[0]))
        if (toRemote) {
            requireTrue(!!auth, 'specify auth token')
            try {
                await postJson(postEndpoint, NO_RESPONSE, map, {
                    Authorization: `Bearer ${auth}`,
                })
                remover(selectedRows.map(e => e[1]))
            } catch (e) {
                if (e instanceof Error) setError(arrayKey, { message: e.message })
            }
        } else setLocalStorage(map)
    }
}

export function selectedItemsOnRemoteDeleter<
    FormContainer extends FieldValues,
    FormRow extends object & FieldArray<FormContainer, RHFKey1<FormContainer>> & Selectable,
>(
    formMethods: UseFormReturn<FormContainer>,
    remover: UseFieldArrayRemove,
    arrayKey: RHFKey<FormContainer>,
    errorKeys: (index: number) => Path<FormContainer>[],
    delEndpoint: string,
    pathEncoder: (row: FormRow) => string,
    invalidatable: Invalidatable | readonly Invalidatable[],
    auth: Nullable<string>,
    concurrencyLimit: number = 2,
): (rows: FormRow[]) => Promise<void> {

    const didDeleteOrUndefined = async (e: FormRow, path: string) =>
        (await del(path, auth ?? '[$invalid$]') ? e : undefined)

    const loader = async (data: FormRow[]): Promise<readonly Nullable<FormRow>[]> =>
        awaitAllWithBackpressure(
            data.map((e) => {
                if (!e.selected)
                    return toAwaitable(e)
                const path = pathEncoder(e)
                return didDeleteOrUndefined(e, `${delEndpoint}/${path}`)
            }), concurrencyLimit)

    return async () => {
        const { getValues, setError, clearErrors } = formMethods
        const data = getValues(arrayKey)
        const newData = await loader(data)
        const removeQ: number[] = []
        for (const [i, d] of newData.entries()) {
            if (!data[i]?.selected) continue
            if (d === undefined) {
                for (const ek of errorKeys(i)) {
                    setError(ek, {
                        message: `couldn't delete`,
                    })
                }
            } else {
                for (const ek of errorKeys(i)) {
                    clearErrors(ek)
                }
                removeQ.push(i)
            }
        }
        if (removeQ.length > 0) {
            remover(removeQ)
            if (Array.isArray(invalidatable))
                invalidatable.forEach((e: Invalidatable) => { e.invalidateAll( )})
            else
                (<Invalidatable>invalidatable).invalidateAll()
        }
    }
}
