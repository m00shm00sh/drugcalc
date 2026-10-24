import { act, renderHook } from '@testing-library/react'
import '@testing-library/jest-dom'

import {
    useLocalBlends,
    useLocalCompounds,
    useLocalFrequencies,
    useLocalConfig,
    useLocalToken
} from '../../src/hooks/useLocalData'

const prepareSingleItemStorage = (k: string, v: string, forceMissing: [boolean]) => {
    const getStorage = window.localStorage.getItem as jest.Mock
    const setStorage = window.localStorage.setItem as jest.Mock
    getStorage.mockReset()
    /* localStorage.getItem is called three times as a result of subscribing to

     * window events and using useEffect internally
     * so we must mock it repeatably instead of once
     */
    getStorage.mockImplementation(kk => kk === k && !forceMissing[0] ? v : null)
    return () => setStorage.mock
}

type StateHook<T> = [T, React.Dispatch<React.SetStateAction<T>>]

async function doCheckMockStorage<R extends string|object>(
    hook: () => StateHook<R>,
    k: string,
    v: R,
    iv: R | undefined
): Promise<void> {
    const forceMissing: [boolean] = [ false ]
    const vv = JSON.stringify(v) // to follow useLocalStorage builtin serializer
    const setStorageMock = prepareSingleItemStorage(k, vv, forceMissing)
    const { result } = renderHook(() => hook())
    expect(result.current.length).toBe(2)
    const [val, setVal] = result.current
    await act(async () => setVal(v))
    expect(setStorageMock().lastCall?.[0]).toEqual(k)
    expect(setStorageMock().lastCall?.[1]).toEqual(vv)
    expect(val).toEqual(v)
    forceMissing[0] = true
    await act(async () => setVal(v))
    expect(result.current[0]).toEqual(iv)
}


it('useLocalBlends', () => doCheckMockStorage(useLocalBlends, 'blends',
    {
        'a': { components: {
            'b': 1,
            'c=d': 2
        }}
    }, {}
))

it('useLocalCompounds', () => doCheckMockStorage(useLocalCompounds, 'compounds',
    {
        'a': { halfLife: 'PT1H' }
    }, {}
))

it('useLocalFrequencies', () => doCheckMockStorage(useLocalFrequencies, 'frequencies',
    {
        'a': { values: [ 'PT1H' ]}
    }, {}
))

it('useLocalConfig', () => doCheckMockStorage(useLocalConfig, 'config',
    {
        tickDuration: 'PT1H'
    }, {}
))

it('useLocalToken', () => doCheckMockStorage(useLocalToken, 'login',
    'a', ''
))