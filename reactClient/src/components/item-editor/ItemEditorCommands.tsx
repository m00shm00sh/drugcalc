import { Button } from '../../widgets/Button'
import { Flex } from '../../widgets/RowCol'

type ItemEditorProps = {
    addRow: () => void
    getSelected: () => number[]
    removeRows: (row?: number[]) => void // unary -> remove rows; nullary -> remove all
    addMsg?: string
    doClear?: boolean
}
export const ItemEditor = ({
    addRow,
    getSelected,
    removeRows,
    addMsg,
    doClear
}: ItemEditorProps) => (
    <Flex dir="row">
        <Button colorClass="sky-400" onClick={() => addRow()}>
            {addMsg ?? 'Add component'}
        </Button>
        <Button colorClass="amber-400" onClick={() => removeRows(getSelected())}>
            Remove selected
        </Button>
        { doClear &&
            <Button colorClass="red-400" onClick={() => removeRows()}>
                Clear
            </Button>
        }
    </Flex>
)
