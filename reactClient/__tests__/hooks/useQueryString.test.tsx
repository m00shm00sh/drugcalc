import { act, renderHook, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom'

import { useQueryString } from '../../src/hooks/useQueryString'

it('should use query string to read value', async () => {
    const q = new URLSearchParams('a=b')
    window.history.pushState({}, '', `p?${q}`)
    const { result } = renderHook(() => useQueryString())
    expect(result.current.length).toBe(2)
    expect(result.current[0]).toEqual(q)
})

it('should use query string to write value', async () => {
    const q = new URLSearchParams('a=b')
    const q2 = new URLSearchParams('c=d')
    const p = '/'
    window.history.pushState({}, '', `${p}?${q}`)
    const { result } = renderHook(() => useQueryString())
    expect(result.current.length).toBe(2)
    act(() => result.current[1](q2))
    await waitFor(() => expect(window.location.search).toEqual(`?${q2}`),
        { interval: 100, timeout: 1000 }
    )
})

/*
export function useQueryString(): [URLSearchParams, Dispatch<SetStateAction<URLSearchParams>>] {
    const first = useRef(true)
    const [p, setP] = useState(new URLSearchParams(window.location.search))
    useEffect(() => {
        if (first.current)
            first.current = false
        else // do not set window.location directly; it will cause infinite refresh and rerender!
            window.history.pushState(null, '', `${window.location.pathname}?${p.toString()}`)
    }, [p])
    return [p, setP]
}

*/