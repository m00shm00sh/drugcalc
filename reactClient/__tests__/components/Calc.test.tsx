import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom'
import { jest } from '@jest/globals'
import fetchMock from 'jest-fetch-mock'
import './__class_list'

jest.mock('plotly.js-gl2d-dist-min/', () => ({}))

import { Calc } from '../../src/components/Calc'
import {
    cachedRemoteBlends,
    cachedRemoteCompounds,
    cachedRemoteFrequenciesWithWeights,
    cachedRemoteVariants,
    cachedTransformerFrequencies,
    cachedTransformerNames,
} from '../../src/util/data-fetcher'
import { mockNextFetch, mockNextFetch404, mockNextFetchHardFail, resetMock } from '../__fetch_utils'

/* useAsyncResult hooks fire in declaration order:
 *   1. initFrequencyNames  -> /api/data/frequencies.w
 *   2. initCompoundNames   -> /api/data/compounds
 *   3. initBlendNames      -> /api/data/blends
 *   4. cachedTransformerNames -> /api/data/transformers/names
 *   5. cachedTransformerFrequencies -> /api/data/transformers/frequencies
 */
const mockAllCalcEndpoints = () => {
    mockNextFetch({'f1': 'PT1H'})        // /api/data/frequencies.w
    mockNextFetch(['c1', 'c2'])          // /api/data/compounds
    mockNextFetch([])                    // /api/data/blends
    mockNextFetch(['t1'])                // /api/data/transformers/names
    mockNextFetch(['tf1'])               // /api/data/transformers/frequencies
}

describe('Calc', () => {
    afterEach(() => {
        cleanup()
        cachedRemoteCompounds.invalidateAll()
        cachedRemoteBlends.invalidateAll()
        cachedRemoteFrequenciesWithWeights.invalidateAll()
        cachedTransformerNames.invalidateAll()
        cachedTransformerFrequencies.invalidateAll()
        cachedRemoteVariants.invalidateAll()
        resetMock()
    })

    it('renders the Cycle heading once data loads', async () => {
        mockAllCalcEndpoints()
        const {findByText} = render(<Calc />)
        await findByText('Cycle')
    })

    it('renders the calculator form once data loads', async () => {
        mockAllCalcEndpoints()
        const {findByRole, findByText} = render(<Calc />)
        await findByText('Cycle')
        await findByRole('button', {name: 'Evaluate'})
    })

    it('does not crash when Evaluate is clicked with an empty form', async () => {
        mockAllCalcEndpoints()
        mockNextFetch({})                // /api/calc
        const {findByRole, findByText} = render(<Calc />)
        await findByText('Cycle')
        await findByRole('button', {name: 'Evaluate'})

        const btn = await findByRole('button', {name: 'Evaluate'}) as HTMLButtonElement
        await act(async () => {
            fireEvent.click(btn)
        })
    })

    it('shows an error when compounds fail to load', async () => {
        mockNextFetch({'f1': 'PT1H'})    // /api/data/frequencies.w (ok)
        mockNextFetch404()               // /api/data/compounds -> 404 -> []
        mockNextFetch([])                // /api/data/blends
        mockNextFetch(['t1'])            // /api/data/transformers/names
        mockNextFetch(['tf1'])           // /api/data/transformers/frequencies
        const {findByText} = render(<Calc />)
        await findByText('could not load compounds or frequencies')
    })

    it('sends correct form data to /api/calc on submit', async () => {
        /* useAsyncResult hooks fire in declaration order:
         *   1. initFrequencyNames  -> /api/data/frequencies.w
         *   2. initCompoundNames   -> /api/data/compounds
         *   3. initBlendNames      -> /api/data/blends
         *   4. cachedTransformerNames -> /api/data/transformers/names
         *   5. cachedTransformerFrequencies -> /api/data/transformers/frequencies
         */
        mockNextFetch({'f1': 'PT1H'})        // 1. frequencies.w
        mockNextFetch(['c1', 'c2'])          // 2. compounds
        mockNextFetch([])                    // 3. blends
        mockNextFetch(['t1'])                // 4. transformer names
        mockNextFetch(['tf1'])               // 5. transformer frequencies
        mockNextFetch(['v1'])                // 6. variants for 'c1' (fetched on compound change)
        mockNextFetch({})                    // 7. /api/calc response

        const {findByText, findByRole, container} = render(<Calc />)
        await findByText('Cycle')
        // Wait for CalcBody (and its form fields) to render
        await findByRole('button', {name: 'Evaluate'})

        // Set compoundOrBlend to 'c1' (triggers variant fetch)
        const compoundSelect = container.querySelector('select[name="cycle.0.compoundOrBlend"]') as HTMLSelectElement
        await act(async () => {
            fireEvent.change(compoundSelect, {target: {value: 'c1'}})
        })

        // Wait for variant options to load from the mocked fetch
        await waitFor(() => {
            expect(container.querySelector('select[name="cycle.0.variantOrTransformer"] option[value="v1"]')).not.toBeNull()
        })

        // Select variant 'v1'
        const variantSelect = container.querySelector('select[name="cycle.0.variantOrTransformer"]') as HTMLSelectElement
        fireEvent.change(variantSelect, {target: {value: 'v1'}})

        // Set dose (number input with valueAsNumber)
        const doseInput = container.querySelector('input[name="cycle.0.dose"]') as HTMLInputElement
        fireEvent.change(doseInput, {target: {value: '10'}})

        // Set start (display duration format '1d' -> converted to ISO8601 'PT24H')
        const startInput = container.querySelector('input[name="cycle.0.start"]') as HTMLInputElement
        fireEvent.change(startInput, {target: {value: '1d'}})

        // Set duration (display duration format '2h' -> converted to ISO8601 'PT2H')
        const durationInput = container.querySelector('input[name="cycle.0.duration"]') as HTMLInputElement
        fireEvent.change(durationInput, {target: {value: '2h'}})

        // Set freqName (defaults to '' from initial values, must be set explicitly)
        const freqSelect = container.querySelector('select[name="cycle.0.freqName"]') as HTMLSelectElement
        fireEvent.change(freqSelect, {target: {value: 'f1'}})

        // Click Evaluate
        const btn = (await findByRole('button', {name: 'Evaluate'})) as HTMLButtonElement
        await act(async () => {
            fireEvent.click(btn)
        })

        // Verify /api/calc was called with the correct body
        const lastCall = fetchMock.mock.lastCall
        expect(lastCall).not.toBeUndefined()
        expect((lastCall![0] as string)).toContain('/api/calc')

        const body = JSON.parse((lastCall![1] as RequestInit).body as string)
        expect(body).toEqual({
            cycle: [{
                compoundOrBlend: 'c1',
                variantOrTransformer: 'v1',
                dose: 10,
                start: 'PT24H',
                duration: 'PT2H',
                freqName: 'f1',
            }],
            decode: 'PT24H',
        })
    })

    it('switches prefix to blend (.b)', async () => {
        mockAllCalcEndpoints()
        mockNextFetch(['v1']) // variants for 'c1' when compound changes
        const {findByText, findByRole, container} = render(<Calc />)
        await findByText('Cycle')
        await findByRole('button', {name: 'Evaluate'})

        // Set compoundOrBlend to 'c1'
        const compoundSelect = container.querySelector('select[name="cycle.0.compoundOrBlend"]') as HTMLSelectElement
        await act(async () => {
            fireEvent.change(compoundSelect, {target: {value: 'c1'}})
        })
        await waitFor(() => {
            expect(container.querySelector('select[name="cycle.0.variantOrTransformer"] option[value="v1"]')).not.toBeNull()
        })

        // Change prefix to '.b' (blend)
        const prefixSelect = container.querySelector('select[name="cycle.0.prefix"]') as HTMLSelectElement
        await act(async () => {
            fireEvent.change(prefixSelect, {target: {value: '.b'}})
        })

        // The dose field should still be present (not stripped for .b)
        expect(container.querySelector('input[name="cycle.0.dose"]')).not.toBeNull()
    })

    it('switches prefix to transformer (.t)', async () => {
        mockAllCalcEndpoints()
        mockNextFetch(['v1']) // variants for 'c1' when compound changes
        const {findByText, findByRole, container} = render(<Calc />)
        await findByText('Cycle')
        await findByRole('button', {name: 'Evaluate'})

        // Set compoundOrBlend to 'c1'
        const compoundSelect = container.querySelector('select[name="cycle.0.compoundOrBlend"]') as HTMLSelectElement
        await act(async () => {
            fireEvent.change(compoundSelect, {target: {value: 'c1'}})
        })
        await waitFor(() => {
            expect(container.querySelector('select[name="cycle.0.variantOrTransformer"] option[value="v1"]')).not.toBeNull()
        })

        // Change prefix to '.t' (transformer)
        const prefixSelect = container.querySelector('select[name="cycle.0.prefix"]') as HTMLSelectElement
        await act(async () => {
            fireEvent.change(prefixSelect, {target: {value: '.t'}})
        })

        // The dose field should NOT be present for .t prefix
        expect(container.querySelector('input[name="cycle.0.dose"]')).toBeNull()
    })

    it('shows error when postJson fails', async () => {
        mockNextFetch({'f1': 'PT1H'})        // 1. frequencies.w
        mockNextFetch(['c1', 'c2'])          // 2. compounds
        mockNextFetch([])                    // 3. blends
        mockNextFetch(['t1'])                // 4. transformer names
        mockNextFetch(['tf1'])               // 5. transformer frequencies
        mockNextFetch(['v1'])                // 6. variants for 'c1'
        mockNextFetchHardFail('calc failed') // 7. /api/calc throws

        const {findByText, findByRole, container} = render(<Calc />)
        await findByText('Cycle')
        await findByRole('button', {name: 'Evaluate'})

        // Set compoundOrBlend to 'c1'
        const compoundSelect = container.querySelector('select[name="cycle.0.compoundOrBlend"]') as HTMLSelectElement
        await act(async () => {
            fireEvent.change(compoundSelect, {target: {value: 'c1'}})
        })
        await waitFor(() => {
            expect(container.querySelector('select[name="cycle.0.variantOrTransformer"] option[value="v1"]')).not.toBeNull()
        })

        // Set dose
        const doseInput = container.querySelector('input[name="cycle.0.dose"]') as HTMLInputElement
        fireEvent.change(doseInput, {target: {value: '10'}})

        // Set start
        const startInput = container.querySelector('input[name="cycle.0.start"]') as HTMLInputElement
        fireEvent.change(startInput, {target: {value: '1d'}})

        // Set duration
        const durationInput = container.querySelector('input[name="cycle.0.duration"]') as HTMLInputElement
        fireEvent.change(durationInput, {target: {value: '2h'}})

        // Set freqName
        const freqSelect = container.querySelector('select[name="cycle.0.freqName"]') as HTMLSelectElement
        fireEvent.change(freqSelect, {target: {value: 'f1'}})

        // Click Evaluate
        const btn = (await findByRole('button', {name: 'Evaluate'})) as HTMLButtonElement
        await act(async () => {
            fireEvent.click(btn)
        })

        // Error should be displayed
        await findByText('calc failed')
    })

    it('saves form to URL via Save button', async () => {
        mockAllCalcEndpoints()
        mockNextFetch(['v1']) // variants for 'c1' when compound changes
        const {findByText, findByRole, container} = render(<Calc />)
        await findByText('Cycle')
        await findByRole('button', {name: 'Evaluate'})

        // Set compoundOrBlend to 'c1'
        const compoundSelect = container.querySelector('select[name="cycle.0.compoundOrBlend"]') as HTMLSelectElement
        await act(async () => {
            fireEvent.change(compoundSelect, {target: {value: 'c1'}})
        })
        await waitFor(() => {
            expect(container.querySelector('select[name="cycle.0.variantOrTransformer"] option[value="v1"]')).not.toBeNull()
        })

        // Click Save form to URL
        const saveBtn = (await findByRole('button', {name: 'Save form to URL'})) as HTMLButtonElement
        await act(async () => {
            fireEvent.click(saveBtn)
        })

        // URL should have been updated with query params
        expect(window.location.search).toContain('cb=c1')
    })

    it('handles malformed query string import', async () => {
        // qFL.length=2, qCBlen=1 → length mismatch → throws in importRowsFromQueryString
        window.history.pushState({}, '', '?fl=b&fl=c&cb=bad&d=1&s=1d&t=&fn=')
        mockAllCalcEndpoints()
        mockNextFetch(['v1']) // variants for 'c1' when compound changes
        const {findByText, findByRole} = render(<Calc />)
        await findByText('Cycle')
        // Should not crash; falls back to newRow()
        await findByRole('button', {name: 'Evaluate'})
    })

    it('switches prefix back to compound from blend', async () => {
        mockAllCalcEndpoints()
        mockNextFetch(['v1']) // variants for 'c1' when compound changes
        mockNextFetch(['v1']) // variants for 'c1' when prefix changes back to ''
        const {findByText, findByRole, container} = render(<Calc />)
        await findByText('Cycle')
        await findByRole('button', {name: 'Evaluate'})

        // Set compoundOrBlend to 'c1'
        const compoundSelect = container.querySelector('select[name="cycle.0.compoundOrBlend"]') as HTMLSelectElement
        await act(async () => {
            fireEvent.change(compoundSelect, {target: {value: 'c1'}})
        })
        await waitFor(() => {
            expect(container.querySelector('select[name="cycle.0.variantOrTransformer"] option[value="v1"]')).not.toBeNull()
        })

        // Change prefix to '.b' first
        const prefixSelect = container.querySelector('select[name="cycle.0.prefix"]') as HTMLSelectElement
        await act(async () => {
            fireEvent.change(prefixSelect, {target: {value: '.b'}})
        })

        // Change prefix back to '' (compound)
        await act(async () => {
            fireEvent.change(prefixSelect, {target: {value: ''}})
        })

        // The dose field should be present again for '' prefix
        expect(container.querySelector('input[name="cycle.0.dose"]')).not.toBeNull()
        // Variant select should show options (variants fetched)
        await waitFor(() => {
            expect(container.querySelector('select[name="cycle.0.variantOrTransformer"] option[value="v1"]')).not.toBeNull()
        })
    })

    it('clicks Add item to append a row', async () => {
        mockAllCalcEndpoints()
        const {findByText, findByRole} = render(<Calc />)
        await findByText('Cycle')
        await findByRole('button', {name: 'Evaluate'})

        // Click Add item to append a new row
        const addBtn = (await findByRole('button', {name: 'Add item'})) as HTMLButtonElement
        await act(async () => {
            fireEvent.click(addBtn)
        })

        // Should now have 2 rows (the variant fetch happens for the new row too)
        // Just verify it doesn't crash
        await findByRole('button', {name: 'Evaluate'})
    })

    it('imports query string with blend prefix', async () => {
        // Query string with prefix '.b' (encoded as 'b')
        window.history.pushState({}, '', '?fl=b&cb=c1&d=10&s=1d&t=2h&fn=f1')
        mockAllCalcEndpoints()
        mockNextFetch([]) // blends for .b prefix (no variants needed)
        const {findByText, findByRole} = render(<Calc />)
        await findByText('Cycle')
        // Should not crash and should render with blend prefix data
        await findByRole('button', {name: 'Evaluate'})
    })

    it('imports query string with transformer prefix', async () => {
        // Query string with prefix '.t' (encoded as 't')
        window.history.pushState({}, '', '?fl=t&cb=c1&d=10&s=1d&t=2h&fn=f1')
        mockAllCalcEndpoints()
        // transformers are already mocked by mockAllCalcEndpoints
        const {findByText, findByRole} = render(<Calc />)
        await findByText('Cycle')
        // Should not crash and should render with transformer prefix data
        await findByRole('button', {name: 'Evaluate'})
    })
})
