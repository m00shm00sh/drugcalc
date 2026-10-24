import { cleanup, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import '../__class_list'

import { ItemEditor } from '../../../src/components/item-editor/ItemEditorCommands'

describe('ItemEditor', () => {
    afterEach(cleanup)
    const f = () => []

    it('creates the appropriate button text', () => {
        const {queryByText} = render(<ItemEditor
            addRow={f} getSelected={f} removeRows={f} doClear
        />)
        const btnAdd = queryByText('Add component') as HTMLButtonElement | null
        const btnDelN = queryByText('Remove selected') as HTMLButtonElement | null
        const btnDelAll = queryByText('Clear') as HTMLButtonElement | null
        expect([btnAdd, btnDelN, btnDelAll]).not.toContain(null)
    })

    it('creates the appropriate custom button text', () => {
        const {queryByText} = render(<ItemEditor
            addRow={f} getSelected={f} removeRows={f} addMsg={'aaa'}
        />)
        const btnAdd = queryByText('aaa') as HTMLButtonElement | null
        expect(btnAdd).not.toBeNull()
    })

    it('omits clear button by default', () => {
        const {queryByText} = render(<ItemEditor
            addRow={f} getSelected={f} removeRows={f}
        />)
        const btnAdd = queryByText('Clear') as HTMLButtonElement | null
        expect(btnAdd).toBeNull()
    })

    it('connects the appropriate functions', () => {
        let addCnt = 0
        let removeSelCnt = 0
        let removeAllCnt = 0
        const _sel = [ 2, 3 ]
        const getSelected = () => _sel
        const doAdd = () => { ++addCnt }
        const doDel = (row?: number[]) => {
            if (row === _sel)
                ++removeSelCnt
            else if (row === undefined)
                ++removeAllCnt
        }
        const {queryByText} = render(<ItemEditor
            addRow={doAdd} getSelected={getSelected} removeRows={doDel} doClear
        />)
        const btnAdd = queryByText('Add component') as HTMLButtonElement | null
        const btnDelN = queryByText('Remove selected') as HTMLButtonElement | null
        const btnDelAll = queryByText('Clear') as HTMLButtonElement | null
        expect([btnAdd, btnDelN, btnDelAll]).not.toContain(null)
        btnAdd?.click()
        btnDelN?.click()
        btnDelAll?.click()
        expect(addCnt).toBe(1)
        expect(removeSelCnt).toBe(1)
        expect(removeAllCnt).toBe(1)
    })

    it('creates a row flexbox', () => {
        const f = () => []
        const {queryByText} = render(<ItemEditor
            addRow={f} getSelected={f} removeRows={f}
        />)
        const parClasses = queryByText('Add component')?.parentElement?.classNamesAsList()
        expect(parClasses).toContain('flex-row')
    })
})
