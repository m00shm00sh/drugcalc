import { Button } from '../../widgets/Button'
import { Flex } from '../../widgets/RowCol'

type ComponentEditorProps = {
    addRow: () => void
    removeSelected: () => void
}
export const ComponentEditor = ({ addRow, removeSelected }: ComponentEditorProps) => (
    <Flex dir="row">
        <Button colorClass="sky-400" onClick={() => addRow()}>
            Add component
        </Button>
        <Button colorClass="amber-400" onClick={() => removeSelected}>
            Remove selected
        </Button>
    </Flex>
)
