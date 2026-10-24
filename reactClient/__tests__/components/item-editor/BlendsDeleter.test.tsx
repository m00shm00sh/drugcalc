import { act, cleanup, fireEvent, getAllByRole, render, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom'
import { resetMock, mockNextFetch, mockNextFetchHardFail, checkParams, checkCorrectEndpoint } from '../../__fetch_utils'

import { BlendsDeleter } from '../../../src/components/item-editor/BlendsDeleter'
import { cachedRemoteBlends } from '../../../src/util/data-fetcher'

describe('BlendsDeleter', () => {
    beforeEach(() => {
        resetMock()
        cachedRemoteBlends.invalidateAll()
    })
    afterEach(cleanup)

    it('displays the list of blends', async () => {
        const theList = ['a', 'b']
        mockNextFetch(theList)
        const {findAllByRole, rerender} = render(<BlendsDeleter loginToken='a' isLoggedIn />)
        act(() => rerender(<BlendsDeleter loginToken='a' isLoggedIn />))
        const opts$ = (await findAllByRole('option') as HTMLOptionElement[]).map(e => e.text)
        expect(opts$).toEqual(theList)
    })

    it('submits the expected value', async () => {
        const theList = ['a', 'b']
        mockNextFetch(theList)
        const {container, findByText, rerender} = render(<BlendsDeleter loginToken='a' isLoggedIn />)
        act(() => rerender(<BlendsDeleter loginToken='a' isLoggedIn />))
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        const selects = getAllByRole(container, 'combobox') as HTMLSelectElement[]
        const checkboxes = getAllByRole(container, 'checkbox') as HTMLInputElement[]
        fireEvent.change(selects[0], {target: {value: 'a'}})
        fireEvent.change(selects[1], {target: {value: 'b'}})
        fireEvent.click(checkboxes[1])
        const btnSubmit = await findByText('Delete from remote') as HTMLButtonElement
        mockNextFetch({})
        fireEvent.click(btnSubmit)
        await waitFor(() => checkCorrectEndpoint(['blends', 'b']))
        checkParams('method', (p) => { expect(p).toEqual('DELETE') })
    })

    it('removes deleted items on submit', async () => {
        const theList = ['a', 'b']
        mockNextFetch(theList)
        const {container, findByText, rerender} = render(<BlendsDeleter loginToken='a' isLoggedIn />)
        act(() => rerender(<BlendsDeleter loginToken='a' isLoggedIn />))
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        const selects = getAllByRole(container, 'combobox') as HTMLSelectElement[]
        const checkboxes = getAllByRole(container, 'checkbox') as HTMLInputElement[]
        fireEvent.change(selects[0], {target: {value: 'a'}})
        fireEvent.change(selects[1], {target: {value: 'b'}})
        fireEvent.click(checkboxes[1])
        const btnSubmit = await findByText('Delete from remote') as HTMLButtonElement
        mockNextFetch({})
        fireEvent.click(btnSubmit)
        await waitFor(() => checkCorrectEndpoint(['blends', 'b']))
        expect(container.querySelectorAll('fieldset').length).toBe(1)
    })

    it('fails as expected when load succeeds but submit fails', async () => {
        const theList = ['a', 'b']
        mockNextFetch(theList)
        const {container, findByText, rerender} = render(<BlendsDeleter loginToken='a' isLoggedIn />)
        act(() => rerender(<BlendsDeleter loginToken='a' isLoggedIn />))
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        const selects = getAllByRole(container, 'combobox') as HTMLSelectElement[]
        const checkboxes = getAllByRole(container, 'checkbox') as HTMLInputElement[]
        fireEvent.change(selects[0], {target: {value: 'a'}})
        fireEvent.change(selects[1], {target: {value: 'b'}})
        fireEvent.click(checkboxes[1])
        const btnSubmit = await findByText('Delete from remote') as HTMLButtonElement
        mockNextFetchHardFail('aaa')
        fireEvent.click(btnSubmit)
        // need a rerender for the fetches so wait till the expected failure occurs
        await waitFor(() => expect(container.querySelectorAll('fieldset.invalid')).toHaveLength(1))
        const invalid = [...container.querySelectorAll('fieldset.invalid').values()]
        expect(invalid).toHaveLength(1)
        const b1s = invalid[0].querySelector('input[name="blends.1.selected"]')
        expect(b1s).not.toBeNull()
    })

    it('fails as expected when load fails', async () => {
        mockNextFetchHardFail('aaaa')
        const {findByText} = render(<BlendsDeleter loginToken='a' isLoggedIn />)
        await findByText('could not load blends')
    })

    it('throws when rendered without a login token', () => {
        expect(() => render(<BlendsDeleter />)).toThrow('this component should not rendered without a login token')
    })

    it('calls getSelected when Remove selected is clicked', async () => {
        const theList = ['a', 'b']
        mockNextFetch(theList)
        const {container, findByText, getByText} = render(<BlendsDeleter loginToken='a' isLoggedIn />)
        const btnAdd = await findByText('Add item') as HTMLButtonElement
        fireEvent.click(btnAdd)
        const checkboxes = getAllByRole(container, 'checkbox') as HTMLInputElement[]
        fireEvent.click(checkboxes[0])
        const btnRemove = getByText('Remove selected') as HTMLButtonElement
        fireEvent.click(btnRemove)
    })
})