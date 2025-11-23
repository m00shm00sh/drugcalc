import { cleanup, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { Centered } from '../../../src/components/widgets/Centered'

describe('Centered', () => {
    afterEach(cleanup)

    it('creates a div with appropriate centering classes', () => {
        const {queryByText} = render(<Centered>a</Centered>)
        const classNames = queryByText('a')?.classNamesAsList()
        expect(classNames).toContain('align-middle')
        expect(classNames).toContain('text-center')
    })

    it('propagates additional classes', () => {
        const {queryByText} = render(<Centered className='aa'>b</Centered>)
        const classNames = queryByText('b')?.classNamesAsList()
        expect(classNames).toContain('align-middle')
        expect(classNames).toContain('aa')
    })

})
