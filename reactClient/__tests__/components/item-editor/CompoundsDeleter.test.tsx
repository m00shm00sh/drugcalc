import { cleanup, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { cachedRemoteCompounds, cachedRemoteVariants } from '../../../src/util/data-fetcher'
import { CompoundsDeleter } from '../../../src/components/item-editor/CompoundsDeleter'
import { mockNextFetch, resetMock } from '../../__fetch_utils'

describe('CompoundsDeleter', () => {
    afterEach(() => {
        cleanup()
        cachedRemoteCompounds.invalidateAll()
        cachedRemoteVariants.invalidateAll()
        resetMock()
    })

    it('throws when rendered without a login token', () => {
        expect(() => render(<CompoundsDeleter />)).toThrow('this component should not rendered without a login token')
    })

    it('renders the compounds deleter heading', async () => {
        mockNextFetch(['a', 'b'])
        const {findByText} = render(<CompoundsDeleter loginToken='tok' />)
        await findByText('Compounds deleter')
    })

    it('renders the Delete from remote button', async () => {
        mockNextFetch(['a', 'b'])
        const {getByRole, findByText} = render(<CompoundsDeleter loginToken='tok' />)
        await findByText('Compounds deleter')
        expect(getByRole('button', {name: 'Delete from remote'})).not.toBeNull()
    })


})
