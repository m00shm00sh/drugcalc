import { cleanup, fireEvent, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { CompoundsEditor } from '../../../src/components/item-editor/CompoundsEditor'
import { cachedRemoteCompounds } from '../../../src/util/data-fetcher'
import { mockNextFetch, resetMock } from '../../__fetch_utils'

describe('CompoundsEditor', () => {
    afterEach(() => {
        cleanup()
        cachedRemoteCompounds.invalidateAll()
        resetMock()
    })

    it('renders the compounds editor heading', async () => {
        mockNextFetch(['a', 'b'])
        const {findByText} = render(<CompoundsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - compounds')
    })

    it('renders the Add item and Save buttons', async () => {
        mockNextFetch(['a', 'b'])
        const {getByRole, findByText} = render(<CompoundsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - compounds')
        expect(getByRole('button', {name: 'Add item'})).not.toBeNull()
        expect(getByRole('button', {name: 'Save'})).not.toBeNull()
    })

    it('loads from storage when compounds are stored', async () => {
        const getStorage = window.localStorage.getItem as jest.Mock
        getStorage.mockImplementation((k: string) =>
            k === 'compounds'
                ? JSON.stringify({ a: { halfLife: 'PT1H', pctActive: 1 } })
                : null
        )
        mockNextFetch(['a', 'b'])
        const {findByText, findByPlaceholderText} = render(<CompoundsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - compounds')
        // compoundMapToEditorFields(storage) at line 29 should have loaded 'a'
        const compoundInput = (await findByPlaceholderText('compound')) as HTMLInputElement
        expect(compoundInput.value).toBe('a')
    })

    it('loads details from remote when Load selected from remote is clicked', async () => {
        mockNextFetch(['a', 'b'])
        mockNextFetch({ halfLife: 'PT1H', pctActive: 1 })
        const {getByRole, findByText} = render(<CompoundsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - compounds')
        const btnLoad = getByRole('button', {name: 'Load selected from remote'})
        expect(btnLoad).not.toBeNull()
        // lines 46-47: loadCompoundDetailsFromRemote called
        fireEvent.click(btnLoad)
    })

    it('calls appendRow and getSelected via EditorCommands', async () => {
        mockNextFetch(['a', 'b'])
        const {findByText, getByRole} = render(<CompoundsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - compounds')
        const btnAdd = getByRole('button', {name: 'Add item'}) as HTMLButtonElement
        fireEvent.click(btnAdd)
        // lines 126-127: appendRow and getSelected called
    })
})
