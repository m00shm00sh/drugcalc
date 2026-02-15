import { cleanup, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import './__class_list'

import { EditorCommands } from '../../src/components/EditorCommands'

describe('EditorCommands', () => {
    afterEach(cleanup)
    const f = () => []
    const fA = async () => {}

    it('creates the appropriate button text', () => {
        const {queryByText, rerender} = render(<EditorCommands
            appendRow={f} getSelected={f} removeRows={f}
        />)
        const btnAdd = queryByText('Add item') as HTMLButtonElement | null
        const btnDelN = queryByText('Remove selected') as HTMLButtonElement | null
        const btnDelAll = queryByText('Clear') as HTMLButtonElement | null
        const btnSave = queryByText('Save') as HTMLButtonElement | null
        expect([btnAdd, btnDelN, btnDelAll, btnSave]).not.toContain(null)

        rerender(<EditorCommands
            isLoggedIn
            appendRow={f} getSelected={f} removeRows={f}
            loadFromRemote={fA} allowCommit={[true, f]}
            /* submitStr and save2 have mandatory strings so don't test their defaults */
        />)
        const btnLoad = queryByText('Load selected from remote')
        const btnAllowCommit = queryByText('toggle commit to remote')
        const btnDoCommit = queryByText('Commit selected to remote')
        expect([btnLoad, btnAllowCommit, btnDoCommit]).not.toContain(null)

        rerender(<EditorCommands
            isLoggedIn
            appendRow={f} getSelected={f} removeRows={f}
            allowCommit={[false, f]}
            /* submitStr and save2 have mandatory strings so don't test their defaults */
        />)
        // this one is different because allowCommit[0] is false instead of allowCommit being nully
        const btnSave2 = queryByText('Save')
        expect([btnSave2]).not.toContain(null)
    })

    it('creates the appropriate custom button text', () => {
        const {queryByText} = render(<EditorCommands
            appendRow={f} getSelected={f} removeRows={f}
            loadFromRemote={['aa', fA]} submitStr='bb' save2={[f, 'cc']}
        />)
        const btnLoad = queryByText('aa')
        const btnSubmit = queryByText('bb')
        const btnSave2 = queryByText('cc')
        expect([btnLoad, btnSubmit, btnSave2]).not.toContain(null)
    })

    it('uses login state for toggle commit button', () => {
        const {queryByText, rerender} = render(<EditorCommands
            appendRow={f} getSelected={f} removeRows={f}
            allowCommit={[true, f]}
            /* submitStr and save2 have mandatory strings so don't test their defaults */
        />)
        const btnAllowCommit = queryByText('toggle commit to remote')
        expect(btnAllowCommit).toBeNull()

        rerender(<EditorCommands
            appendRow={f} getSelected={f} removeRows={f}
            isLoggedIn
            allowCommit={[true, f]}
            /* submitStr and save2 have mandatory strings so don't test their defaults */
        />)
        const btnAllowCommit1 = queryByText('toggle commit to remote')
        expect(btnAllowCommit1).not.toBeNull()
    })

    it('connects the appropriate functions', () => {
        /* transitive <ItemEditor /> */
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
        /* second row */
        let loadRemoteCnt = 0
        let setAllowCommitCnt = 0
        const doLoadRemote = async () => { ++loadRemoteCnt }
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const doSetAllowCommit = (_b: React.SetStateAction<boolean>) => { ++setAllowCommitCnt }
        const {queryByText, rerender} = render(<EditorCommands
            appendRow={doAdd} getSelected={getSelected} removeRows={doDel}
            isLoggedIn
            loadFromRemote={doLoadRemote} allowCommit={[true, doSetAllowCommit]}
        />)
        const btnAdd = queryByText('Add item') as HTMLButtonElement | null
        const btnDelN = queryByText('Remove selected') as HTMLButtonElement | null
        const btnDelAll = queryByText('Clear') as HTMLButtonElement | null
        const btnLoad = queryByText('Load selected from remote') as HTMLButtonElement | null
        const btnToggleCommit = queryByText('toggle commit to remote') as HTMLButtonElement | null
        expect([
            btnAdd, btnDelN, btnDelAll,
            btnLoad, btnToggleCommit
        ]).not.toContain(null)
        btnAdd?.click()
        btnDelN?.click()
        btnDelAll?.click()
        btnLoad?.click()
        btnToggleCommit?.click()
        expect(addCnt).toBe(1)
        expect(removeSelCnt).toBe(1)
        expect(removeAllCnt).toBe(1)
        expect(loadRemoteCnt).toBe(1)
        expect(setAllowCommitCnt).toBe(1)

        let save2Cnt = 0
        const doSave2 = () => { ++ save2Cnt }

        // test save2 must be done in rerender with allowCommit === undefined
        rerender(<EditorCommands
            appendRow={doAdd} getSelected={getSelected} removeRows={doDel}
            loadFromRemote={doLoadRemote} save2={[doSave2, 'aa']}
        />)

        const btnSave2 = queryByText('aa') as HTMLButtonElement | null
        expect([btnSave2]).not.toContain(null)
        btnSave2?.click()
        expect(save2Cnt).toBe(1)
    })

    it('forbids both allowCommit and save2 to be specified', () => {
        expect(() => {
            render(<EditorCommands
                isLoggedIn
                appendRow={f} getSelected={f} removeRows={f}
                loadFromRemote={fA} allowCommit={[true, f]}
                save2={[f, '']}
            />)
        }).toThrow()
    })
})
