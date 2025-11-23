import { cleanup, render, } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { Button } from '../../../src/components/widgets/Button'

describe('Button', () => {
    afterEach(cleanup)

    it('has default style classes', () => {
        const {queryByText} = render(
            <Button type='button'>button</Button>
        )
        const classNames = queryByText('button')?.classNamesAsList()
        expect(classNames).toContain('rounded-xl')
        expect(classNames).toContain('p-2')
    })

    it('propagates custom classes', () => {
        const {queryByText} = render(
            <Button type='button' className='aa'>button</Button>
        )
        const classNames = queryByText('button')?.classNamesAsList()
        expect(classNames).toContain('rounded-xl')
        expect(classNames).toContain('aa')
    })

    it('renders with semantic color class', () => {
        const {queryByText} = render(
            <Button type='button' colorClass='add-item'>button</Button>
        )
        const classNames = queryByText('button')?.classNamesAsList()
        expect(classNames).toContain('bg-blue-500')
        expect(classNames).not.toContain('add-item')
    })

    it('defaults to type=button', () => {
        const {queryByText} = render(
            <Button>b</Button>
        )
        expect((queryByText('b') as HTMLButtonElement|null)?.type).toBe('button')
    })
    it('handles all button types', () => {
        const {queryByText} = render(<>
            <Button type='button'>b1</Button>
            <Button type='reset'>b2</Button>
            <Button type='submit'>b3</Button>
        </>)
        expect((queryByText('b1') as HTMLButtonElement|null)?.type).toBe('button')
        expect((queryByText('b2') as HTMLButtonElement|null)?.type).toBe('reset')
        expect((queryByText('b3') as HTMLButtonElement|null)?.type).toBe('submit')
    })
})
