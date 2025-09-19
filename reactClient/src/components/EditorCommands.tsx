import { Button } from '../widgets/Button'
import type { EditorProps } from './EditorProps'

type EditorCommandsProps = EditorProps & {
    appendRow: () => void
    getSelected: () => number[]
    removeRows: (row?: number[]) => void // unary -> remove rows; nullary -> remove all
    loadFromRemote?: () => Promise<void>
    allowCommit?: boolean
    setAllowCommit?: (value: React.SetStateAction<boolean>) => void
    submitStr?: string
}

export const EditorCommands = ({
    isLoggedIn,
    appendRow,
    getSelected,
    removeRows,
    loadFromRemote,
    allowCommit,
    setAllowCommit,
    submitStr,
}: EditorCommandsProps) => (
    <div className="grid grid-cols-3">
        <Button onClick={appendRow}>Add</Button>
        <Button onClick={() => removeRows(getSelected())}>
            Remove selected
        </Button>
        {/* we need a nullary indirection so removeAll is called with the correct arity */}
        <Button onClick={() => removeRows()}>Clear</Button>
        {loadFromRemote !== undefined && (
            <Button onClick={loadFromRemote}>Load selected from remote</Button>
        )}
        <Button
            {...(!loadFromRemote && { className: 'col-start-2' })}
            type="submit"
        >
            {submitStr ? submitStr : allowCommit ? 'Commit' : 'Save'}
        </Button>
        {isLoggedIn && setAllowCommit !== undefined && (
            <Button
                className="col-span-2"
                onClick={() => setAllowCommit(!allowCommit)}
            >
                toggle commit to remote
            </Button>
        )}
    </div>
)
