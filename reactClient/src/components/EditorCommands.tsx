import { Button } from '../widgets/Button'
import { Flex } from '../widgets/RowCol'

export type EditorProps = {
    isLoggedIn: boolean
    loginToken?: string
}

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
        allowCommit ? () => allowCommit[1](!allowCommit[0]) : undefined
    return (
        <Flex dir='col'>
            <Flex dir='row'>
                <Button colorClass='sky-400' onClick={appendRow}>
                    Add
                </Button>
                <Button colorClass='amber-400' onClick={() => removeRows(getSelected())}>
                    Remove selected
                </Button>
                {/* we need a nullary indirection so removeAll is called with the correct arity */}
                <Button colorClass='red-400' onClick={() => removeRows()}>
                    Clear
                </Button>
            </Flex>
            <Flex dir='row'>
                {loadFromRemote !== undefined && (
                    <Button colorClass='indigo-400' onClick={loadFromRemote}>
                        Load selected from remote
                    </Button>
                )}
                <Button
                    {...(!loadFromRemote && { className: 'col-start-2' })}
                    colorClass={loadFromRemote ? 'blue-500' : 'emerald-400'}
                    type="submit"
                >
                    {submitStr ? submitStr : (allowCommit ?? [])[0] ? 'Commit selected to remote' : 'Save'}
                </Button>
                {isLoggedIn && toggleCommit !== undefined && (
                    <Button colorClass='zinc-600' onClick={toggleCommit}>
                        toggle commit to remote
                    </Button>
                )}
                {save2 !== undefined && (
                    <Button colorClass='blue-500' onClick={save2[0]}>
                        {save2[1]}
                    </Button>
                )}
            </Flex>
        </Flex>
    )
}
