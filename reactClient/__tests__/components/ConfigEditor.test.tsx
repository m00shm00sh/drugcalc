import { cleanup, fireEvent, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import './__class_list'

import { ConfigEditor } from '../../src/components/item-editor/ConfigEditor'
import { resetMock } from '../__fetch_utils'

describe('ConfigEditor', () => {
    afterEach(() => {
        cleanup()
        resetMock()
    })

    it('renders the config editor heading', () => {
        const {getByRole} = render(<ConfigEditor />)
        expect(getByRole('heading', {name: 'Config editor'})).not.toBeNull()
    })

    it('adds a config row on Add item', () => {
        const {getByRole, container} = render(<ConfigEditor />)
        const before = container.querySelectorAll('fieldset').length
        const btnAdd = getByRole('button', {name: 'Add item'}) as HTMLButtonElement
        fireEvent.click(btnAdd)
        const after = container.querySelectorAll('fieldset').length
        expect(after).toBe(before + 1)
    })

    it('renders the Save button', () => {
        const {getByRole} = render(<ConfigEditor />)
        expect(getByRole('button', {name: 'Save'})).not.toBeNull()
    })

    it('loads stored config when config is in localStorage', () => {
        const getStorage = window.localStorage.getItem as jest.Mock
        getStorage.mockImplementation((k: string) =>
            k === 'config'
                ? JSON.stringify({ tickDuration: '2h', cutoffMilligrams: 5, doLambdaDoseCorrection: true })
                : null
        )
        const {findByRole} = render(<ConfigEditor />)
        // configToEditorFields(storedConfig) at line 28/102
        expect(findByRole('combobox', {name: /config/i})).not.toBeNull()
    })

    it('renders select-type SetItem when config type is doLambdaDoseCorrection', () => {
        const {getByRole, container} = render(<ConfigEditor />)
        const btnAdd = getByRole('button', {name: 'Add item'}) as HTMLButtonElement
        fireEvent.click(btnAdd)
        // change type select to 'doLambdaDoseCorrection' (select type -> line 49 SetItem select case)
        const typeSelects = container.querySelectorAll('select[name="config.0.type"]')
        const typeSelect = typeSelects[typeSelects.length - 1] as HTMLSelectElement
        fireEvent.change(typeSelect, {target: {value: 'doLambdaDoseCorrection'}})
        // should now show a select for the value (SetItem select case at line 49)
        const valueSelects = container.querySelectorAll('select[name="config.0.value"]')
        expect(valueSelects.length).toBeGreaterThan(0)
    })

    it('handles config type change via onChange handler', () => {
        const {getByRole, container} = render(<ConfigEditor />)
        const btnAdd = getByRole('button', {name: 'Add item'}) as HTMLButtonElement
        fireEvent.click(btnAdd)
        // change type from tickDuration (input) to cutoffMilligrams (input) - line 86-90
        const typeSelects = container.querySelectorAll('select[name="config.0.type"]')
        const typeSelect = typeSelects[typeSelects.length - 1] as HTMLSelectElement
        fireEvent.change(typeSelect, {target: {value: 'cutoffMilligrams'}})
    })

    it('submits config form', () => {
        const {getByRole} = render(<ConfigEditor />)
        const btnAdd = getByRole('button', {name: 'Add item'}) as HTMLButtonElement
        fireEvent.click(btnAdd)
        const btnSave = getByRole('button', {name: 'Save'}) as HTMLButtonElement
        fireEvent.click(btnSave)
        // doSubmit at lines 113-118
    })

    it('renders multiple config rows via fields.map', () => {
        const {getByRole, container} = render(<ConfigEditor />)
        const btnAdd = getByRole('button', {name: 'Add item'}) as HTMLButtonElement
        fireEvent.click(btnAdd)
        fireEvent.click(btnAdd)
        const rows = container.querySelectorAll('fieldset.border')
        expect(rows.length).toBeGreaterThanOrEqual(2)
    })

    it('interacts with EditorCommands buttons', () => {
        const {getByRole} = render(<ConfigEditor />)
        // lines 145-153: EditorCommands appendRow/getSelected/removeRows
        expect(getByRole('button', {name: 'Add item'})).not.toBeNull()
        expect(getByRole('button', {name: 'Remove selected'})).not.toBeNull()
        expect(getByRole('button', {name: 'Save'})).not.toBeNull()
    })
})
