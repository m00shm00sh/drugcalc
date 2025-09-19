import type { Dispatch, SetStateAction } from 'react'
import { useLocalStorage } from 'usehooks-ts'

import type { BlendsMap } from '../types/Blends'
import type { CompoundsMap } from '../types/Compounds'
import type { FrequenciesMap } from '../types/Frequencies'

function filterOutDeleter<T>(
    uls: [T, Dispatch<SetStateAction<T>>, () => void],
): [T, Dispatch<SetStateAction<T>>] {
    return [uls[0], uls[1]]
}

export const useLocalBlends = () =>
    filterOutDeleter(useLocalStorage<BlendsMap>('blends', {}))
export const useLocalCompounds = () =>
    filterOutDeleter(useLocalStorage<CompoundsMap>('compounds', {}))
export const useLocalFrequencies = () =>
    filterOutDeleter(useLocalStorage<FrequenciesMap>('frequencies', {}))
