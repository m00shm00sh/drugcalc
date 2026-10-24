import { act, cleanup, fireEvent, getAllByRole, render, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { FrequenciesDeleter } from '../../../src/components/item-editor/FrequenciesDeleter'
import { cachedRemoteFrequenciesWithWeights } from '../../../src/util/data-fetcher'
import { mockNextFetch, mockNextFetchHardFail, resetMock } from '../../__fetch_utils'

describe('FrequenciesDeleter', () => {
    afterEach(() => {
        cleanup()
        cachedRemoteFrequenciesWithWeights.invalidateAll()
        resetMock()
    })

    it('throws when rendered without a login token', () => {
        expect(() => render(<FrequenciesDeleter />)).toThrow('this component should not rendered without a login token')
    })

    it('renders the frequencies deleter heading', async () => {
        mockNextFetch({'f1': 'PT1H'})
        const {findByText} = render(<FrequenciesDeleter loginToken='tok' />)
        await findByText('Frequencies deleter')
    })

    it('renders the Delete from remote button', async () => {
        mockNextFetch({'f1': 'PT1H'})
        const {getByRole, findByText} = render(<FrequenciesDeleter loginToken='tok' />)
        await findByText('Frequencies deleter')
        expect(getByRole('button', {name: 'Delete from remote'})).not.toBeNull()
    })

    it('renders the frequency checkbox input', async () => {
        mockNextFetch({'f1': 'PT1H'})
        const {container, findByText} = render(<FrequenciesDeleter loginToken='tok' />)
        await findByText('Frequencies deleter')
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        const checkboxes = getAllByRole(container, 'checkbox')
        expect(checkboxes.length).toBeGreaterThan(0)
    })

    it('submits delete for selected frequencies', async () => {
        mockNextFetch({'f1': 'PT1H'})
        mockNextFetch({})
        const {container, findByText} = render(<FrequenciesDeleter loginToken='tok' />)
        await findByText('Frequencies deleter')
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        const checkboxes = getAllByRole(container, 'checkbox') as HTMLInputElement[]
        fireEvent.click(checkboxes[0])
        const btnSubmit = await findByText('Delete from remote') as HTMLButtonElement
        fireEvent.click(btnSubmit)
    })

    it('handles delete failure gracefully', async () => {
        mockNextFetch({'f1': 'PT1H'})
        mockNextFetchHardFail('delete failed')
        const {container, findByText} = render(<FrequenciesDeleter loginToken='tok' />)
        await findByText('Frequencies deleter')
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        await act(async () => fireEvent.click(btnAdd))
        const checkboxes = getAllByRole(container, 'checkbox') as HTMLInputElement[]
        await act(async () => fireEvent.click(checkboxes[0]))
        const btnSubmit = await findByText('Delete from remote') as HTMLButtonElement
        await act(async () => fireEvent.click(btnSubmit))
        await waitFor(() => expect(container.querySelectorAll('fieldset.invalid').length).toBeGreaterThanOrEqual(1))
    })

    it('interacts with EditorCommands buttons', async () => {
        mockNextFetch({'f1': 'PT1H'})
        const {findByText, findAllByRole} = render(<FrequenciesDeleter loginToken='tok' />)
        await findByText('Frequencies deleter')
        const addButtons = await findAllByRole('button', {name: 'Add item'})
        expect(addButtons.length).toBeGreaterThanOrEqual(1)
        const removeButtons = await findAllByRole('button', {name: 'Remove selected'})
        expect(removeButtons.length).toBeGreaterThanOrEqual(1)
    })
})
