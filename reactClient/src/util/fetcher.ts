import { z } from 'zod'
import { BACKEND } from './constants'
import type { ValueOrSupplier } from './filter-setif'
import type { Nullable } from './util'

export async function fetchJson<T extends object, ZT extends z.ZodType<T> = z.ZodType<T>>(
    relativeLink: string,
    schema: ZT,
    on404: ValueOrSupplier<z.infer<typeof schema>>,
): Promise<z.infer<typeof schema>>

export async function fetchJson<T extends object, ZT extends z.ZodType<T> = z.ZodType<T>>(
    relativeLink: string,
    schema: ZT,
): Promise<Nullable<z.infer<typeof schema>>>

export async function fetchJson<T extends object, ZT extends z.ZodType<T> = z.ZodType<T>>(
    relativeLink: string,
    schema: ZT,
    on404?: ValueOrSupplier<z.infer<typeof schema>>,
): Promise<Nullable<z.infer<typeof schema>>> {
    const response = await fetch(`${BACKEND}${relativeLink}`, {
        headers: {
            Accept: 'application/json',
        },
    })
    if (!response.ok && response.status !== 404) {
        throw Error(`couldn't fetch ${relativeLink}: ${response.status}`)
    }
    if (response.status === 404) {
        if (on404 === undefined) return undefined
        if (typeof on404 === 'function') return on404()
        return on404 as z.infer<typeof schema>
    }
    const data = await response.json()
    return schema.parse(data)
}

export const NO_RESPONSE = z.undefined()

export async function postJson<R, T extends object, ZT extends z.ZodType<R> = z.ZodType<R>>(
    relativeLink: string,
    responseSchema: ZT,
    body: T,
    auxHeaders: Record<string, string> = {},
): Promise<z.infer<typeof responseSchema>> {
    const response = await fetch(`${BACKEND}${relativeLink}`, {
        method: 'POST',
        headers: {
            Accept: 'application/json',
            'Content-type': 'application/json',
            ...auxHeaders,
        },
        body: JSON.stringify(body),
    })
    if (!response.ok) {
        throw Error(`couldn't post ${relativeLink}: ${response.status}`)
    }
    if (<unknown>responseSchema === NO_RESPONSE) {
        await response.text()
        return undefined as z.infer<typeof responseSchema>
    }

    const data = await response.json()
    return responseSchema.parse(data)
}

export async function del(
    relativeLink: string,
    bearer: string,
): Promise<boolean> {
    if (!bearer)
        throw Error(`missing bearer`)
    const response = await fetch(`${BACKEND}${relativeLink}`, {
        method: 'DELETE',
        headers: {
            'Authorization': `Bearer ${bearer}`
        },
    })
    if (!response.ok) {
        if (response.status === 404)
            return false
        throw Error(`couldn't delete ${relativeLink}: ${response.status}`)
    }
    await response.text()
    return true
}
