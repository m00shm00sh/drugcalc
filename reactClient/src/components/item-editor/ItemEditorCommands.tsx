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
        <Button colorClass="add-item" onClick={() => addRow()}>
            {addMsg ?? 'Add component'}
        </Button>
        <Button colorClass="remove-item" onClick={() => removeRows(getSelected())}>
            Remove selected
        </Button>
        { doClear &&
            <Button colorClass="remove-all-items" onClick={() => removeRows()}>
                Clear
            </Button>
        }
    </Flex>
)
