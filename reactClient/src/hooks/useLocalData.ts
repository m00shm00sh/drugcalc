import type { z } from 'zod'
import type { Dispatch, SetStateAction } from 'react'
import { useLocalStorage } from 'usehooks-ts'

import { BlendsMapSchema } from '../types/Blends'
import { CompoundsMapSchema } from '../types/Compounds'
import { FrequenciesMapSchema } from '../types/Frequencies'
import { ConfigSchema } from '../types/Config'

type ULSReturn<T> = [T, Dispatch<SetStateAction<T>>, () => void]

function filterOutDeleter<T>(uls: ULSReturn<T>): [T, Dispatch<SetStateAction<T>>] {
    return [uls[0], uls[1]]
}

function zodDeserialize<T extends object, ZT extends z.ZodType<T> = z.ZodType<T>>(
    item: string,
    schema: ZT,
): z.infer<typeof schema> {
    return schema.parse(JSON.parse(item))
}

type valueOrSupplier<T extends object> = T | (() => T)

function useLocalStorageWithZodDeserializer<
    T extends object,
    ZT extends z.ZodType<T> = z.ZodType<T>,
>(
    key: string,
    schema: ZT,
    defaultValue: valueOrSupplier<z.infer<typeof schema>>,
): ULSReturn<z.infer<typeof schema>> {
    return useLocalStorage(key, defaultValue, {
        deserializer: (s: string) => zodDeserialize(s, schema),
    })
}

export const useLocalBlends = () =>
    filterOutDeleter(useLocalStorageWithZodDeserializer('blends', BlendsMapSchema, {}))
export const useLocalCompounds = () =>
    filterOutDeleter(useLocalStorageWithZodDeserializer('compounds', CompoundsMapSchema, {}))
export const useLocalFrequencies = () =>
    filterOutDeleter(useLocalStorageWithZodDeserializer('frequencies', FrequenciesMapSchema, {}))
export const useLocalConfig = () =>
    filterOutDeleter(useLocalStorageWithZodDeserializer('config', ConfigSchema, {}))
