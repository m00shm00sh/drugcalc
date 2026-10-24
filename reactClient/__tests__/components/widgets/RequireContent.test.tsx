import { cleanup, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { RequireContent } from '../../../src/components/widgets/RequireContent'

describe('RequireContent', () => {
    afterEach(cleanup)

    it('should render child on success', () => {
        const {queryByText} = render(<RequireContent predicate={true} errorText='b'><p>a</p></RequireContent>)
        expect(queryByText('a')).not.toBeNull()
        expect(queryByText('a')?.classNamesAsList()).not.toContain('text-red-600')
    })

    it('should render error on failure', () => {
        const {queryByText} = render(<RequireContent predicate={false} errorText='b'><p>a</p></RequireContent>)
        expect(queryByText('b')).not.toBeNull()
        expect(queryByText('b')?.classNamesAsList()).toContain('text-red-600')
    })

    it('should rerender on success', () => {
        const {queryByText, rerender} = render(<RequireContent predicate={false} errorText='b'><p>a</p></RequireContent>)
        expect(queryByText('b')).not.toBeNull()
        expect(queryByText('b')?.classNamesAsList()).toContain('text-red-600')
        rerender(<RequireContent predicate={true} errorText='b'><p>a</p></RequireContent>)
        expect(queryByText('a')).not.toBeNull()
        expect(queryByText('a')?.classNamesAsList()).not.toContain('text-red-600')
        expect(queryByText('b')).toBeNull()
    })
})
