import { Button } from '../widgets/Button'
import type { EditorProps } from './EditorProps'

type EditorCommandsProps = EditorProps & {
    appendRow: () => void
    getSelected: () => number[]
    removeRows: (row?: number[]) => void // unary -> remove rows; nullary -> remove all
    loadFromRemote?: () => Promise<void>
    allowCommit?: [boolean, (value: React.SetStateAction<boolean>) => void]
    submitStr?: string
    save2?: [() => void, string]
}

export const EditorCommands = ({
    isLoggedIn,
    appendRow,
    getSelected,
    removeRows,
    loadFromRemote,
    allowCommit,
    submitStr,
    save2,
}: EditorCommandsProps) => {
    if (allowCommit && save2)
        throw Error("incompatible usage: both allowCommit and save2 are specified")
    const toggleCommit =
        allowCommit ? () => allowCommit[1](allowCommit[0]) : undefined
    return (
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
                {submitStr ? submitStr : (allowCommit ?? [])[0] ? 'Commit' : 'Save'}
            </Button>
            {isLoggedIn && toggleCommit !== undefined && (
                <Button onClick={toggleCommit}>
                    toggle commit to remote
                </Button>
            )}
            {save2 !== undefined && (
                <Button onClick={save2[0]}>
                    {save2[1]}
                </Button>
            )}
        </div>
    )
}
