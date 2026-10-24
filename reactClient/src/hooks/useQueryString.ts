import type { Dispatch, SetStateAction } from 'react'
import { useEffect, useRef, useState } from 'react'

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