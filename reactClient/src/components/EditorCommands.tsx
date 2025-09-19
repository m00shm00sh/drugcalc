import { Column, Row } from '../widgets/RowCol'
import { Button } from '../widgets/Button'
import type { EditorProps } from './EditorProps'

type EditorCommandsProps = EditorProps & {
    appendRow: () => void;
    removeAll: () => void;
    loadFromRemote: () => Promise<void>;
    allowCommit: boolean;
    setAllowCommit: (value: React.SetStateAction<boolean>) => void;
};

export const EditorCommands = ({
    isLoggedIn,
    appendRow,
    removeAll,
    loadFromRemote,
    allowCommit,
    setAllowCommit,
}: EditorCommandsProps) => (
    <Column grid>
        <Row grid>
            <Button type="button" onClick={appendRow}>
                Add
            </Button>
            {/* we need a nullary indirection so removeAll is called with the correct arity */}
            <Button type="button" onClick={() => removeAll()}>
                Clear
            </Button>
        </Row>
        <Row grid>
            <Button type="button" onClick={loadFromRemote}>
                Load
            </Button>
            <Button type="submit">{allowCommit ? 'Commit' : 'Save'}</Button>
        </Row>
        {isLoggedIn && (
            <Button type="button" onClick={() => setAllowCommit(!allowCommit)}>
                toggle commit to remote
            </Button>
        )}
    </Column>
)
