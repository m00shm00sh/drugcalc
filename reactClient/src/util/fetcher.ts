import { z } from 'zod'
import type { ValueOrSupplier } from './filter-setif'
import type { Nullable } from './util'

function fallbackOrUndefined<T>(fallback: ValueOrSupplier<T> | undefined): Nullable<T> {
    if (fallback === undefined) return undefined
    if (typeof fallback === 'function') return (fallback as () => T)()
    return fallback as T
}

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
    on404OrHardFail?: ValueOrSupplier<z.infer<typeof schema>>,
): Promise<Nullable<z.infer<typeof schema>>> {
    let response: Response | undefined
    try {
        response = await fetch(relativeLink, {
            headers: {
                Accept: 'application/json',
            },
        })
    } catch (e) {
        console.log(`fetch error(GET ${relativeLink}): ${e}`)
        return fallbackOrUndefined(on404OrHardFail)
    }

    if (!response.ok && response.status !== 404) {
        throw Error(`couldn't fetch ${relativeLink}: ${response.status}`)
    }
    if (response.status === 404) {
        return fallbackOrUndefined(on404OrHardFail)
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
    const response = await fetch(relativeLink, {
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
    let response: Response
    try {
        response = await fetch(relativeLink, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${bearer}`
            },
        })
    } catch (e) {
        console.log(`fetch error(DELETE ${relativeLink}): ${e}`)
        return false
    }
    if (!response.ok) {
        if (response.status === 404)
            return false
        throw Error(`couldn't delete ${relativeLink}: ${response.status}`)
    }
    await response.text()
    return true
}
