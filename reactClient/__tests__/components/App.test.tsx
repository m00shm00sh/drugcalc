import { cleanup, render } from '@testing-library/react'
import { jest } from '@jest/globals'
import '@testing-library/jest-dom'
import './__class_list'

jest.mock('react-tabs/style/react-tabs.css', () => {})
jest.mock('../../src/App.css', () => {})
jest.mock('plotly.js-gl2d-dist-min/', () => ({}))

import App from '../../src/App'
import {
    cachedRemoteBlends,
    cachedRemoteCompounds,
    cachedRemoteFrequenciesWithWeights,
    cachedRemoteVariants,
    cachedTransformerFrequencies,
    cachedTransformerNames,
} from '../../src/util/data-fetcher'
import { mockNextFetch, resetMock } from '../__fetch_utils'

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

describe('App', () => {
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

    it('renders the app shell and the Calc tab', async () => {
        mockAllCalcEndpoints()
        const {findByText} = render(<App />)
        await findByText('Cycle')
    })
})
