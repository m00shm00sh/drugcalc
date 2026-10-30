import { describe, expect, it, jest } from '@jest/globals'

jest.unstable_mockModule('../../../../src/util/fetcher', () => ({
    del: jest.fn(),
    postJson: jest.fn(),
    NO_RESPONSE: Symbol('NO_RESPONSE'),
}))
jest.unstable_mockModule('../../../../src/util/awaitAllWithBackpressure', () => ({
    awaitAllWithBackpressure: jest.fn(async (awaitables: readonly Promise<unknown>[]) =>
        Promise.all(awaitables)
    ),
    toAwaitable: jest.fn((o: any) => Promise.resolve(o)),
}))

const {
    selectedItemsFromRemoteFormLoader,
    selectedItemsToRemoteSender,
    selectedItemsOnRemoteDeleter,
} = await import('../../../../src/components/item-editor/util/remote-load-store')
const { del, postJson } = await import('../../../../src/util/fetcher')
const { awaitAllWithBackpressure } = await import('../../../../src/util/awaitAllWithBackpressure')

const mockDel = del as any
const mockPostJson = postJson as any
const mockAwaitAll = awaitAllWithBackpressure as any

describe('selectedItemsFromRemoteFormLoader', () => {
    it('sets error and resets values when fetch returns undefined', async () => {
        mockAwaitAll.mockResolvedValue([undefined])
        const formMethods: any = {
            getValues: jest.fn(() => [{ selected: true }]),
            setError: jest.fn(),
            clearErrors: jest.fn(),
        }
        const fetchDetails: any = async () => undefined
        const errorKeys = (i: number) => [`items.${i}.name`]
        const resetValues = (r: any) => r
        const updater = jest.fn()

        const loader = selectedItemsFromRemoteFormLoader(
            formMethods,
            'items' as any,
            fetchDetails as any,
            errorKeys as any,
            resetValues as any,
            updater,
        )
        await loader()

        expect(formMethods.setError).toHaveBeenCalledWith('items.0.name', {
            message: 'couldn\'t fetch name',
        })
        expect(updater).toHaveBeenCalledWith(0, { selected: true })
    })

    it('clears errors and updates row when fetch succeeds', async () => {
        const updatedRow = { selected: false, name: 'test' }
        mockAwaitAll.mockResolvedValue([updatedRow])
        const formMethods: any = {
            getValues: jest.fn(() => [{ selected: true }]),
            setError: jest.fn(),
            clearErrors: jest.fn(),
        }
        const fetchDetails: any = async () => updatedRow
        const errorKeys = (i: number) => [`items.${i}.name`]
        const resetValues = (r: any) => r
        const updater = jest.fn()

        const loader = selectedItemsFromRemoteFormLoader(
            formMethods,
            'items' as any,
            fetchDetails as any,
            errorKeys as any,
            resetValues as any,
            updater,
        )
        await loader()

        expect(formMethods.clearErrors).toHaveBeenCalledWith('items.0.name')
        expect(updater).toHaveBeenCalledWith(0, { ...updatedRow, selected: false })
    })

    it('skips non-selected rows', async () => {
        mockAwaitAll.mockResolvedValue([{ selected: false }])
        const formMethods: any = {
            getValues: jest.fn(() => [{ selected: false }]),
            setError: jest.fn(),
            clearErrors: jest.fn(),
        }
        const fetchDetails = jest.fn()
        const errorKeys = jest.fn()
        const resetValues = jest.fn()
        const updater = jest.fn()

        const loader = selectedItemsFromRemoteFormLoader(
            formMethods,
            'items' as any,
            fetchDetails as any,
            errorKeys as any,
            resetValues as any,
            updater,
        )
        await loader()

        expect(fetchDetails).not.toHaveBeenCalled()
        expect(formMethods.setError).not.toHaveBeenCalled()
        expect(updater).not.toHaveBeenCalled()
    })
})

describe('selectedItemsToRemoteSender', () => {
    it('posts to remote when toRemote is true and auth is provided', async () => {
        mockPostJson.mockResolvedValue(undefined)
        const formMethods: any = { setError: jest.fn() }
        const remover = jest.fn()
        const mapEncoder = (rows: any[]) => ({ items: rows })
        const setLocalStorage = jest.fn()

        const sender = selectedItemsToRemoteSender(
            formMethods,
            remover as any,
            'items' as any,
            mapEncoder as any,
            '/api/data/items',
            setLocalStorage,
            true,
            'token-123',
        )
        await sender([{ selected: true, name: 'a' }] as any)

        expect(mockPostJson).toHaveBeenCalledWith(
            '/api/data/items',
            expect.anything(),
            { items: [{ selected: true, name: 'a' }] },
            { Authorization: 'Bearer token-123' },
        )
        expect(remover).toHaveBeenCalledWith([0])
    })

    it('sets error when postJson throws', async () => {
        mockPostJson.mockRejectedValue(new Error('network error'))
        const formMethods: any = { setError: jest.fn() }
        const remover = jest.fn()
        const mapEncoder = (rows: any[]) => ({ items: rows })
        const setLocalStorage = jest.fn()

        const sender = selectedItemsToRemoteSender(
            formMethods,
            remover as any,
            'items' as any,
            mapEncoder as any,
            '/api/data/items',
            setLocalStorage,
            true,
            'token-123',
        )
        await sender([{ selected: true, name: 'a' }] as any)

        expect(formMethods.setError).toHaveBeenCalledWith('items', { message: 'network error' })
        expect(remover).not.toHaveBeenCalled()
    })

    it('throws requireTrue when toRemote is true but no auth', async () => {
        const formMethods: any = { setError: jest.fn() }
        const remover = jest.fn()
        const mapEncoder = jest.fn()
        const setLocalStorage = jest.fn()

        const sender = selectedItemsToRemoteSender(
            formMethods,
            remover as any,
            'items' as any,
            mapEncoder as any,
            '/api/data/items',
            setLocalStorage,
            true,
            undefined,
        )
        await expect(sender([{ selected: true }])).rejects.toThrow('specify auth token')
    })

    it('calls setLocalStorage when toRemote is false', async () => {
        mockPostJson.mockResolvedValue(undefined)
        const formMethods: any = { setError: jest.fn() }
        const remover = jest.fn()
        const mapEncoder = (rows: any[]) => ({ items: rows })
        const setLocalStorage = jest.fn()

        const sender = selectedItemsToRemoteSender(
            formMethods,
            remover as any,
            'items' as any,
            mapEncoder as any,
            '/api/data/items',
            setLocalStorage,
            false,
            undefined,
        )
        await sender([{ selected: true, name: 'a' }, { selected: false, name: 'b' }] as any)

        expect(setLocalStorage).toHaveBeenCalledWith({ items: [{ selected: true, name: 'a' }, { selected: false, name: 'b' }] })
        expect(mockPostJson).not.toHaveBeenCalled()
        expect(remover).not.toHaveBeenCalled()
    })
})

describe('selectedItemsOnRemoteDeleter', () => {
    it('sets error when delete returns undefined', async () => {
        const formMethods: any = {
            getValues: jest.fn(() => [{ selected: true }]),
            setError: jest.fn(),
            clearErrors: jest.fn(),
        }
        const remover = jest.fn()
        const errorKeys = (i: number) => [`items.${i}.name`]
        const pathEncoder = (r: any) => r.name

        const deleter = selectedItemsOnRemoteDeleter(
            formMethods,
            remover as any,
            'items' as any,
            errorKeys as any,
            '/api/data/items',
            pathEncoder as any,
            { invalidateAll: jest.fn() } as any,
            'token-123',
        )
        await deleter([{ selected: true, name: 'a' }] as any)

        expect(mockDel.mock.calls.length).toBeGreaterThan(0)
    })

    it('removes rows and invalidates cache when delete succeeds', async () => {
        mockDel.mockResolvedValueOnce(true)
        const formMethods: any = {
            getValues: jest.fn(() => [{ selected: true }]),
            setError: jest.fn(),
            clearErrors: jest.fn(),
        }
        const remover = jest.fn()
        const errorKeys = (i: number) => [`items.${i}.name`]
        const pathEncoder = (r: any) => r.name
        const invalidatable = { invalidateAll: jest.fn() } as any

        const deleter = selectedItemsOnRemoteDeleter(
            formMethods,
            remover as any,
            'items' as any,
            errorKeys as any,
            '/api/data/items',
            pathEncoder as any,
            invalidatable,
            'token-123',
        )
        await deleter([{ selected: true, name: 'a' }] as any)

        expect(formMethods.clearErrors).toHaveBeenCalledWith('items.0.name')
        expect(remover).toHaveBeenCalledWith([0])
        expect(invalidatable.invalidateAll).toHaveBeenCalled()
    })

    it('calls invalidateAll on each item when invalidatable is an array', async () => {
        mockDel.mockResolvedValueOnce(true)
        const formMethods: any = {
            getValues: jest.fn(() => [{ selected: true }]),
            setError: jest.fn(),
            clearErrors: jest.fn(),
        }
        const remover = jest.fn()
        const errorKeys = (i: number) => [`items.${i}.name`]
        const pathEncoder = (r: any) => r.name
        const inv1 = { invalidateAll: jest.fn() }
        const inv2 = { invalidateAll: jest.fn() }
        const invalidatable = [inv1, inv2] as any

        const deleter = selectedItemsOnRemoteDeleter(
            formMethods,
            remover as any,
            'items' as any,
            errorKeys as any,
            '/api/data/items',
            pathEncoder as any,
            invalidatable,
            'token-123',
        )
        await deleter([{ selected: true, name: 'a' }] as any)

        expect(inv1.invalidateAll).toHaveBeenCalled()
        expect(inv2.invalidateAll).toHaveBeenCalled()
        expect(remover).toHaveBeenCalledWith([0])
    })

    it('skips non-selected rows', async () => {
        mockDel.mockResolvedValue(true)
        const formMethods: any = {
            getValues: jest.fn(() => [{ selected: false }]),
            setError: jest.fn(),
            clearErrors: jest.fn(),
        }
        const remover = jest.fn()
        const errorKeys = jest.fn() as any
        const pathEncoder = jest.fn() as any
        const invalidatable = { invalidateAll: jest.fn() } as any

        const deleter = selectedItemsOnRemoteDeleter(
            formMethods,
            remover as any,
            'items' as any,
            errorKeys,
            '/api/data/items',
            pathEncoder,
            invalidatable,
            'token-123',
        )
        await deleter([{ selected: false }])

        expect(mockDel).not.toHaveBeenCalled()
        expect(remover).not.toHaveBeenCalled()
        expect(invalidatable.invalidateAll).not.toHaveBeenCalled()
    })
})
