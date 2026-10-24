import { cleanup, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { ErrorMessage, BigErrorMessage } from '../../../src/components/widgets/ErrorMessage'

describe('*ErrorMessage', () => {
    afterEach(cleanup)

    it('creates a centered div with small red text', () => {
        const {queryByText} = render(<ErrorMessage text="e" />)
        const classNames = queryByText('e')?.classNamesAsList()
        expect(classNames).toContain('align-middle')
        expect(classNames).toContain('text-sm')
        expect(classNames).toContain('text-red-600')
    })

    it('creates a centered div with big red text', () => {
        const {queryByText} = render(<BigErrorMessage text="ee" />)
        const classNames = queryByText('ee')?.classNamesAsList()
        expect(classNames).toContain('align-middle')
        expect(classNames).toContain('text-xl')
        expect(classNames).toContain('text-red-600')
    })
})
