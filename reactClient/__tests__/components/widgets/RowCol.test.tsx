import { cleanup, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { Flex, Grid } from '../../../src/components/widgets/RowCol'

describe('Grid', () => {
    afterEach(cleanup)

    it('should create grid', () => {
        const {queryByText} = render(<Grid colSpec='nc-1'>a</Grid>)
        expect(queryByText('a')?.classNamesAsList()).toContain('grid')
    })
    it('should expand col-spec', () => {
        const {queryByText} = render(<Grid colSpec='nc-2'>a</Grid>)
        expect(queryByText('a')?.classNamesAsList()).toContain('grid-cols-2')
    })

    it('should propagate custom classes', () => {
        const {queryByText} = render(<Grid colSpec='nc-2' auxClasses={['bbb']}>a</Grid>)
        expect(queryByText('a')?.classNamesAsList()).toContain('bbb')
    })
})

describe('Flex', () => {
    it('should create centered flexbox', () => {
        const {queryByText} = render(<Flex dir='col'>a</Flex>)
        expect(queryByText('a')?.classNamesAsList()).toContain('flex')
        expect(queryByText('a')?.classNamesAsList()).toContain('justify-center')
    })

    it('should expand dir', () => {
        const {queryByText} = render(<Flex dir='col'>a</Flex>)
        expect(queryByText('a')?.classNamesAsList()).toContain('flex-col')
    })

    it('should propagate custom classes', () => {
        const {queryByText} = render(<Flex dir='row' auxClasses={['bbb']}>a</Flex>)
        expect(queryByText('a')?.classNamesAsList()).toContain('bbb')
    })
})