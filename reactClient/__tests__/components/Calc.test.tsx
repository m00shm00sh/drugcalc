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
import { mockNextFetch, mockNextFetch404, resetMock } from '../__fetch_utils'

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
})
