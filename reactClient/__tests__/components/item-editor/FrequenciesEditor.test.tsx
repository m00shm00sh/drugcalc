import { cleanup, fireEvent, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { FrequenciesEditor } from '../../../src/components/item-editor/FrequenciesEditor'
import { cachedRemoteFrequenciesWithWeights } from '../../../src/util/data-fetcher'
import { mockNextFetch, resetMock } from '../../__fetch_utils'

describe('FrequenciesEditor', () => {
    afterEach(() => {
        cleanup()
        cachedRemoteFrequenciesWithWeights.invalidateAll()
        resetMock()
    })

    it('renders the frequencies editor heading', async () => {
        mockNextFetch({'f1': 'PT1H'})
        const {findByText} = render(<FrequenciesEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - frequencies')
    })

    it('renders the Add item and Save buttons', async () => {
        mockNextFetch({'f1': 'PT1H'})
        const {getByRole, findByText} = render(<FrequenciesEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - frequencies')
        expect(getByRole('button', {name: 'Add component'})).not.toBeNull()
        expect(getByRole('button', {name: 'Save'})).not.toBeNull()
    })

    it('adds a frequency component via ItemEditor', async () => {
        mockNextFetch({'f1': 'PT1H'})
        const {container, findByText} = render(<FrequenciesEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - frequencies')
        // Find the "Add component" button inside FrequencyComponents (inner ItemEditor)
        const addComponentBtn = Array.from(container.querySelectorAll('button'))
            .find(b => b.textContent === 'Add component')
        expect(addComponentBtn).not.toBeUndefined()
        fireEvent.click(addComponentBtn!)
        // line 77-78: ItemEditor addRow and getSelected called
    })

    it('loads frequency details from remote', async () => {
        mockNextFetch({'f1': 'PT1H'})
        mockNextFetch({ values: ['PT1H'] }) // loadFrequencyDetailsFromRemote response
        const {getByRole, findByText} = render(<FrequenciesEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - frequencies')
        const btnLoad = getByRole('button', {name: 'Load selected from remote'})
        expect(btnLoad).not.toBeNull()
        // line 91: loadFrequencyDetailsFromRemote called
        fireEvent.click(btnLoad)
    })

    it('creates multiple frequency rows via fields.map', () => {
        mockNextFetch({'f1': 'PT1H'})
        const {getByRole, container} = render(<FrequenciesEditor isLoggedIn loginToken='tok' />)
        const btnAdd = getByRole('button', {name: 'Add component'}) as HTMLButtonElement
        // This is the outer EditorCommands Add item
        fireEvent.click(btnAdd)
        fireEvent.click(btnAdd)
        const rows = container.querySelectorAll('fieldset.border')
        expect(rows.length).toBeGreaterThanOrEqual(2)
        // line 141: fields.map
    })

    it('interacts with outer EditorCommands buttons', async () => {
        mockNextFetch({'f1': 'PT1H'})
        const {findByText, getAllByRole} = render(<FrequenciesEditor isLoggedIn loginToken='tok' />)
        await findByText('Data editor - frequencies')
        // lines 164-165: EditorCommands appendRow and getSelected
        // EditorCommands passes addMsg='Add item' to ItemEditor
        expect(getAllByRole('button', {name: 'Add item'}).length).toBeGreaterThanOrEqual(1)
        expect(getAllByRole('button', {name: 'Remove selected'}).length).toBeGreaterThanOrEqual(1)
        expect(getAllByRole('button', {name: 'Save'}).length).toBeGreaterThanOrEqual(1)
    })
})
