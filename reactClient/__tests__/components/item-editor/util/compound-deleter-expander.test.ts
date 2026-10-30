import { describe, expect, it, jest } from '@jest/globals'

jest.unstable_mockModule('../../../../src/util/awaitAllWithBackpressure', () => ({
    awaitAllWithBackpressure: jest.fn(),
    toAwaitable: jest.fn(),
}))
jest.unstable_mockModule('../../../../src/util/data-fetcher', () => ({
    cachedRemoteVariantsOrNull: jest.fn(),
}))

const { expandExpansionItems } = await import('../../../../src/components/item-editor/util/compound-deleter-expander')
const { awaitAllWithBackpressure } = await import('../../../../src/util/awaitAllWithBackpressure')
const { cachedRemoteVariantsOrNull } = await import('../../../../src/util/data-fetcher')

const mockAwaitAll = awaitAllWithBackpressure as any
const mockCachedRemoteVariantsOrNull = cachedRemoteVariantsOrNull as any

const makeFormMethods = (data: any[]) => ({
    getValues: jest.fn(() => data),
    setError: jest.fn(),
    clearErrors: jest.fn(),
})

const makeArrayMethods = () => ({
    update: jest.fn(),
    insert: jest.fn(),
})

describe('expandExpansionItems', () => {
    beforeEach(() => {
        jest.clearAllMocks()
    })

    it('skips non-expanded rows', async () => {
        mockAwaitAll.mockResolvedValue([[]])
        const formMethods = makeFormMethods([{ compound: 'a', expand: false }])
        const arrayMethods = makeArrayMethods()

        const expand = expandExpansionItems(formMethods as any, arrayMethods as any)
        await expand()

        expect(arrayMethods.update).not.toHaveBeenCalled()
        expect(arrayMethods.insert).not.toHaveBeenCalled()
        expect(formMethods.setError).not.toHaveBeenCalled()
    })

    it('caches variants when expand is true and vx is undefined', async () => {
        mockAwaitAll.mockResolvedValue([['v1', 'v2']])
        mockCachedRemoteVariantsOrNull.mockResolvedValue(['v1', 'v2'])
        const formMethods = makeFormMethods([{ compound: 'a', expand: true }])
        const arrayMethods = makeArrayMethods()

        const expand = expandExpansionItems(formMethods as any, arrayMethods as any)
        await expand()

        expect(mockCachedRemoteVariantsOrNull).toHaveBeenCalledWith('a')
        expect(arrayMethods.update).toHaveBeenCalledWith(0, {
            compound: 'a',
            selected: true,
            variant: 'v1',
        })
        expect(arrayMethods.insert).toHaveBeenCalledWith(1, [
            { compound: 'a', variant: 'v2', selected: true },
        ])
    })

    it('sets error when variant fetch returns undefined', async () => {
        mockAwaitAll.mockResolvedValue([undefined])
        const formMethods = makeFormMethods([{ compound: 'a', expand: true }])
        const arrayMethods = makeArrayMethods()

        const expand = expandExpansionItems(formMethods as any, arrayMethods as any)
        await expand()

        expect(formMethods.setError).toHaveBeenCalledWith('compounds.0.compound', {
            message: 'couldn\'t fetch variants',
        })
        expect(arrayMethods.update).not.toHaveBeenCalled()
    })

    it('uses cached vx when expand is true and vx is defined', async () => {
        mockAwaitAll.mockResolvedValue([['cached-v1']])
        const formMethods = makeFormMethods([{ compound: 'a', expand: true, vx: ['cached-v1'] }])
        const arrayMethods = makeArrayMethods()

        const expand = expandExpansionItems(formMethods as any, arrayMethods as any)
        await expand()

        expect(mockCachedRemoteVariantsOrNull).not.toHaveBeenCalled()
        expect(arrayMethods.update).toHaveBeenCalledWith(0, {
            compound: 'a',
            selected: true,
            variant: 'cached-v1',
        })
    })

    it('clears errors for all variant rows after update', async () => {
        mockAwaitAll.mockResolvedValue([['v1', 'v2']])
        const formMethods = makeFormMethods([{ compound: 'a', expand: true }])
        const arrayMethods = makeArrayMethods()

        const expand = expandExpansionItems(formMethods as any, arrayMethods as any)
        await expand()

        expect(formMethods.clearErrors).toHaveBeenCalledWith(['compounds.0', 'compounds.1'])
    })

    it('does not insert when there are no additional variants', async () => {
        mockAwaitAll.mockResolvedValue([['v1']])
        const formMethods = makeFormMethods([{ compound: 'a', expand: true }])
        const arrayMethods = makeArrayMethods()

        const expand = expandExpansionItems(formMethods as any, arrayMethods as any)
        await expand()

        expect(arrayMethods.insert).not.toHaveBeenCalled()
        expect(arrayMethods.update).toHaveBeenCalledWith(0, {
            compound: 'a',
            selected: true,
            variant: 'v1',
        })
    })

    it('processes multiple expanded rows in reverse order', async () => {
        mockAwaitAll.mockResolvedValue([['v1'], ['v2', 'v3']])
        const formMethods = makeFormMethods([
            { compound: 'a', expand: true },
            { compound: 'b', expand: true },
        ])
        const arrayMethods = makeArrayMethods()

        const expand = expandExpansionItems(formMethods as any, arrayMethods as any)
        await expand()

        expect(arrayMethods.update).toHaveBeenCalledWith(1, {
            compound: 'b',
            selected: true,
            variant: 'v2',
        })
        expect(arrayMethods.insert).toHaveBeenCalledWith(2, [
            { compound: 'b', variant: 'v3', selected: true },
        ])
        expect(arrayMethods.update).toHaveBeenCalledWith(0, {
            compound: 'a',
            selected: true,
            variant: 'v1',
        })
    })
})
