import { ItemEditor } from './item-editor/ItemEditorCommands'
import { Button } from './widgets/Button'
import { Flex } from './widgets/RowCol'

export type EditorProps = {
    isLoggedIn?: boolean
    loginToken?: string
}

type EditorCommandsProps = EditorProps & {
    appendRow: () => void
    getSelected: () => number[]
    removeRows: (row?: number[]) => void // unary -> remove rows; nullary -> remove all
    loadFromRemote?: (() => Promise<void>)|[string, () => Promise<void>]
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
        throw Error('incompatible usage: both allowCommit and save2 are specified')
    const toggleCommit = allowCommit ? () => allowCommit[1](!allowCommit[0]) : undefined
    return (
        <Flex dir="col">
            <ItemEditor
                addRow={appendRow} getSelected={getSelected} removeRows={removeRows}
                addMsg='Add item'
                doClear
            />
            <Flex dir="row">
                {loadFromRemote !== undefined && (
                    <Button
                        colorClass="pull-selected-items"
                        onClick={Array.isArray(loadFromRemote)
                            ? loadFromRemote[1]
                            : loadFromRemote
                        }
                    >
                        {Array.isArray(loadFromRemote)
                            ? loadFromRemote[0]
                            : "Load selected from remote"
                        }
                    </Button>
                )}
                <Button
                    colorClass={(allowCommit ?? [])[0] ? 'push-selected-items' : 'save-local'}
                    type="submit"
                >
                    {submitStr
                        ? submitStr
                        : (allowCommit ?? [])[0]
                            ? 'Commit selected to remote'
                            : 'Save'
                    }
                </Button>
                {isLoggedIn && toggleCommit !== undefined && (
                    <Button colorClass="toggle-commit" onClick={toggleCommit}>
                        toggle commit to remote
                    </Button>
                )}
                {save2 !== undefined && (
                    <Button colorClass="save-local" onClick={save2[0]}>
                        {save2[1]}
                    </Button>
                )}
            </Flex>
        </Flex>
    )
}
