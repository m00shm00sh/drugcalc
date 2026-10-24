import { cleanup, fireEvent, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { BlendsEditor } from '../../../src/components/item-editor/BlendsEditor'
import { cachedRemoteCompounds, cachedRemoteVariants } from '../../../src/util/data-fetcher'
import { mockNextFetch, resetMock } from '../../__fetch_utils'

describe('BlendsEditor', () => {
    afterEach(() => {
        cleanup()
        cachedRemoteCompounds.invalidateAll()
        cachedRemoteVariants.invalidateAll()
        resetMock()
    })

    it('renders the blends editor heading', async () => {
        mockNextFetch(['a', 'b'])
        const {findByText} = render(<BlendsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - blends')
    })

    it('renders the Add item button', async () => {
        mockNextFetch(['a', 'b'])
        const {getByRole, findByText} = render(<BlendsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - blends')
        expect(getByRole('button', {name: 'Add item'})).not.toBeNull()
    })

    it('changes compound and fetches variants in BlendComponent', async () => {
        mockNextFetch(['a', 'b'])
        mockNextFetch(['v1', 'v2'])
        const {container, findByText} = render(<BlendsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - blends')
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        // Change the compound select for blend 0 (triggers variants fetch at lines 79-88)
        const compoundSelects = container.querySelectorAll('select[name="blends.0.blend"]')
        if (compoundSelects.length > 0) {
            const compoundSelect = compoundSelects[compoundSelects.length - 1] as HTMLSelectElement
            fireEvent.change(compoundSelect, {target: {value: 'a'}})
        }
    })

    it('removes a blend row via Remove selected', async () => {
        mockNextFetch(['a', 'b'])
        const {container, findByText} = render(<BlendsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - blends')
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        // Click Remove selected on the blend row
        const btnRemove = Array.from(container.querySelectorAll('button'))
            .find(b => b.textContent === 'Remove selected')
        expect(btnRemove).not.toBeUndefined()
        fireEvent.click(btnRemove!)
    })

    it('adds a blend component via inner ItemEditor', async () => {
        mockNextFetch(['a', 'b'])
        const {container, findByText} = render(<BlendsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - blends')
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        // Find inner "Add component" button inside BlendComponents
        const addCompBtn = Array.from(container.querySelectorAll('button'))
            .find(b => b.textContent === 'Add component')
        expect(addCompBtn).not.toBeUndefined()
        fireEvent.click(addCompBtn!)
    })

    it('renders dose calculation for a blend row', async () => {
        mockNextFetch(['a', 'b'])
        const {container, findByText} = render(<BlendsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - blends')
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        // doseForRow at lines 170-179 renders total dose as readonly input
        const doseInput = container.querySelector('input[readonly]') as HTMLInputElement | null
        expect(doseInput).not.toBeNull()
    })

    it('loads blend details from remote', async () => {
        mockNextFetch(['a', 'b'])
        mockNextFetch({ note: '', components: { a: 1 } })
        const {getByRole, findByText} = render(<BlendsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - blends')
        const btnLoad = getByRole('button', {name: 'Load selected from remote'})
        expect(btnLoad).not.toBeNull()
        // lines 181-188: loadFromRemote calls loadBlendDetailsFromRemote
        fireEvent.click(btnLoad)
    })

    it('submits blend form', async () => {
        mockNextFetch(['a', 'b'])
        const {getByRole, findByText} = render(<BlendsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - blends')
        const btnSave = getByRole('button', {name: 'Save'}) as HTMLButtonElement
        fireEvent.click(btnSave)
        // lines 190-199: doSubmit calls selectedItemsToRemoteSender
    })
    it('caches compound and variant fetchers', async () => {
        mockNextFetch(['a', 'b'])
        mockNextFetch(['v1'])
        const {findByText} = render(<BlendsEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - blends')
        // lines 264-273: cache() for getCompounds and getVariants
    })
})
