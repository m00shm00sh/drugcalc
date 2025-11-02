import { zodResolver } from '@hookform/resolvers/zod'
import classNames from 'classnames'
import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { FormProvider, useFieldArray, useForm, useFormContext } from 'react-hook-form'
import createPlotlyComponent from 'react-plotly.js/factory'
import { makeInvoker, useAsyncResult } from '../hooks/useAsyncFetch'
import {
    useLocalBlends,
    useLocalCompounds,
    useLocalConfig,
    useLocalFrequencies,
} from '../hooks/useLocalData'
import { useQueryString } from '../hooks/useQueryString'
import type { BlendsMap } from '../types/Blends'
import type { CalcRequestDataContainer, CalcRequestRow, PlotlyInvocation } from '../types/Calc'
import {
    calcRequestContainerToRequest,
    CalcRequestDataContainerSchema,
    calcRequestRowInit,
    CycleResultSchema,
    exportRowsToQueryString,
    importRowsFromQueryString,
    plotlyEmptyInvocation,
    prefixOptions,
    printCB,
    printVX,
    resultToPlotly,
} from '../types/Calc'
import { reshapeCompoundKeys, type CompoundsMap } from '../types/Compounds'
import type { Config } from '../types/Config'
import { sortedFrequenciesWithWeights, type FrequenciesMap } from '../types/Frequencies'
import { getSelectedIndices } from './item-editor/util/Selectable'
import {
    fetchBlends,
    fetchCompounds,
    fetchFrequencies,
    cachedTransformerFrequencies,
    cachedTransformerNames,
    fetchVariants,
    cachedRemoteVariants,
} from '../util/data-fetcher'
import { getOrElse, stripIf } from '../util/filter-setif'
import cache from '../util/cache'
import { awaitAllWithBackpressure, toAwaitable } from '../util/awaitAllWithBackpressure'
import type { Nullable } from '../util/util'
import { EditorCommands } from './EditorCommands'
import { FormInput, FormSelectWithOptions } from './FormField'
import { Centered } from './widgets/Centered'
import { ErrorMessage } from './widgets/ErrorMessage'
import { RequireContent } from './widgets/RequireContent'
import { Flex, Grid } from './widgets/RowCol'
import { postJson } from '../util/fetcher'
// either this or a dummy typescript declaration file;
// use "regular" Plotly from react-plotly.js for development
// eslint-disable-next-line @typescript-eslint/no-require-imports
const Plotly = require('plotly.js-gl2d-dist-min/')
const Plot = createPlotlyComponent(Plotly)

type CycleDescriptionEditorRowProps = {
    index: number
    update: (row: CalcRequestRow) => void
}

const MergedCompoundNamesContext = createContext<readonly string[]>([])
const MergedBlendNamesContext = createContext<readonly string[]>([])
const MergedFrequencyNamesContext = createContext<readonly string[]>([])
const VariantsFetcherContext = createContext(cachedRemoteVariants)

const CycleDescriptionEditorRow = ({
    index,
    update,
}: CycleDescriptionEditorRowProps) => {
    const {
        getValues,
        formState: { errors },
    } = useFormContext<CalcRequestDataContainer>()

    const compounds = useContext(MergedCompoundNamesContext)
    const getVariants = useContext(VariantsFetcherContext)
    const blends = useContext(MergedBlendNamesContext)
    const frequencies = useContext(MergedFrequencyNamesContext)

    const row = `cycle.${index}`
    const curRow = getValues(`cycle.${index}`)
    const selectPrefix = prefixOptions

    const rowError = errors?.cycle?.[index]
    return (
        <fieldset
            className={classNames(
                'border',
                rowError && 'invalid'
            )}
        >
            <Grid colSpec="nc-4" auxClasses={['gap-2', 'justify-items-center']}>
                <FormInput
                    type="checkbox"
                    label=" "
                    name={`cycle.${index}.selected`}
                    inpClasses={['pl-2']}
                />
                <FormSelectWithOptions
                    label="prefix"
                    name={`${row}.prefix`}
                    optionValues={selectPrefix}
                    onChange={async (e) => {
                        const newPrefix = e.currentTarget.value
                        stripIf('variantOrTransformer' in curRow, curRow, 'variantOrTransformer')
                        stripIf('dose' in curRow, curRow, 'dose')
                        curRow.fn = frequencies
                        switch (newPrefix) {
                            case '':
                                /* we used getValues to get value at start of value,
                                 * not watch to get live value, so we have to assign
                                 * the changed value because it will be stale otherwise
                                 */
                                curRow.prefix = ''
                                curRow.cb = compounds
                                // this check is silly and redundant but welcome to typescript
                                if (curRow.prefix === '')
                                    curRow.vx = await getVariants(curRow.compoundOrBlend)
                                break
                            case '.b':
                                curRow.prefix = '.b'
                                curRow.cb = blends
                                break
                            case '.t':
                                curRow.prefix = '.t'
                                curRow.cb = compounds
                                // this check is silly and redundant but welcome to typescript
                                if (curRow.prefix === '.t')
                                    curRow.vx = await cachedTransformerNames()
                                curRow.fn = [...curRow.fn, ...await cachedTransformerFrequencies()]
                                break
                        }
                        update(curRow)
                    }}
                />
                <FormSelectWithOptions
                    label={printCB(curRow)}
                    name={`${row}.compoundOrBlend`}
                    optionValues={getOrElse(curRow, 'cb', [])}
                    onChange={async (e) => {
                        const newCompound = e.currentTarget.value
                        if (curRow.prefix === '')
                            curRow.vx = await getVariants(newCompound)
                        update({...curRow})
                    }}
                />
                <FormSelectWithOptions
                    label={printVX(curRow)}
                    name={`${row}.variantOrTransformer`}
                    optionValues={getOrElse(curRow, 'vx', [])}
                />
                {curRow.prefix !== '.t' && (
                    <FormInput
                        label="dose"
                        placeholder="dose (mg)"
                        blockClasses={['row-2', 'col-1']}
                        name={`${row}.dose`}
                        type="number"
                        min={0.0001}
                        step={0.0001}
                    />
                )}
                <FormInput
                    blockClasses={['row-2', 'col-2']}
                    placeholder="iso8601-ish"
                    label="start"
                    type="text"
                    name={`${row}.start`}
                />
                <FormInput
                    blockClasses={['row-2', 'col-3']}
                    placeholder="iso8601-ish"
                    label="duration"
                    type="text"
                    name={`${row}.duration`}
                />
                <FormSelectWithOptions
                    blockClasses={['row-2', 'col-4']}
                    label="frequency"
                    name={`${row}.freqName`}
                    optionValues={getOrElse(curRow, 'fn', [])}
                />
            </Grid>
        </fieldset>
    )
}

type CalcBodyProps = {
    hydratedInitValues: () => Promise<CalcRequestDataContainer>
    newRow: () => CalcRequestRow
    localData: {
        config: Config,
        compounds: CompoundsMap,
        blends: BlendsMap,
        frequencies: FrequenciesMap
    }
    saveToQS: (r: CalcRequestRow[]) => void
}
const CalcBody = ({hydratedInitValues, newRow, localData, saveToQS} : CalcBodyProps) => {
    const compounds = useContext(MergedCompoundNamesContext)

    const methods = useForm<CalcRequestDataContainer>({
        defaultValues: hydratedInitValues,
        resolver: zodResolver(CalcRequestDataContainerSchema),
    })
    const {
        control,
        formState: { errors },
        getValues,
        setError,
        watch,
    } = methods
    const { append, update, remove, fields } = useFieldArray({
        control,
        name: `cycle`,
    })

    const [result, setResult] = useState<PlotlyInvocation>(plotlyEmptyInvocation)

    const doSubmit = async (r: CalcRequestDataContainer) => {
        try {
            const plot = await postJson(
                '/api/calc',
                CycleResultSchema,
                calcRequestContainerToRequest(
                    r,
                    localData.config,
                    localData.compounds,
                    localData.blends,
                    localData.frequencies,
                ),
            )
            setResult(resultToPlotly(plot))
        } catch (e) {
            if (e instanceof Error) {
                const resultErr = e.message.substring(
                    e.message.indexOf(':', e.message.indexOf(':') + 1) + 1,
                )
                setError('cycle', { message: resultErr })
            } else {
                alert(e)
            }
        }
    }

    const selecteds = getSelectedIndices(watch, `cycle`)
    return (
        <Centered>
            <FormProvider {...methods}>
                <form onSubmit={methods.handleSubmit((e) => doSubmit(e))}>
                    {fields.map((field, index) => (
                        <CycleDescriptionEditorRow
                            key={field.id}
                            index={index}
                            update={(e) => update(index, e)}
                        />
                    ))}
                    <ErrorMessage text={errors?.cycle?.message} />
                    <EditorCommands
                        appendRow={() => {
                            const r = newRow()
                            const c = compounds
                            r.cb = c
                            append([r])
                        }}
                        getSelected={() => selecteds}
                        removeRows={remove}
                        submitStr="Evaluate"
                        save2={[
                            () => {saveToQS(getValues('cycle'))},
                            'Save form to URL'
                        ]}
                    />
                </form>
            </FormProvider>
            {result.data.length > 0 && <Plot data={result.data} layout={result.layout} />}
        </Centered>
    )
}

export const Calc = () => {
    const [storedConfig] = useLocalConfig()
    const [localBlends] = useLocalBlends()
    const [localCompounds] = useLocalCompounds()
    const [localFrequencies] = useLocalFrequencies()
    const [localCompoundsFromStorage] = useLocalCompounds()
    const localCompoundNames = useMemo(
        () => reshapeCompoundKeys(localCompoundsFromStorage),
        [localCompoundsFromStorage],
    )

    // for useAsyncFetch, auxDeps = [localCompoundNames]
    const getCompounds = cache(
        async () => await fetchCompounds(localCompoundNames),
        () => '',
        60000,
    )

    // for useAsyncFetch, auxDeps = [localCompoundNames]
    const getVariants = cache(
        async (c: string) => await fetchVariants(localCompoundNames, c),
        (s) => s,
        60000,
    )


    const [localBlendsFromStorage] = useLocalBlends()
    const localBlendNames = useMemo(
        () => Object.keys(localBlendsFromStorage),
        [localBlendsFromStorage],
    )
    // for useAsyncFetch, auxDeps = [localBlendNames]
    const getBlends = cache(
        async () => await fetchBlends(localBlendNames),
        () => '',
        60000,
    )

    const [localFrequenciesFromStorage] = useLocalFrequencies()
    const localFrequencyItems = useMemo(
        () => sortedFrequenciesWithWeights(localFrequenciesFromStorage),
        [localFrequenciesFromStorage],
    )
    // for useAsyncFetch, auxDeps = [localFrequencyItems]
    const getFrequencies = cache(
        async () => await fetchFrequencies(localFrequencyItems),
        () => '',
    )

    const initFrequencyNames = useAsyncResult(makeInvoker({
        func: getFrequencies,
        args: [],
        initial: [],
        auxDeps: [localFrequencyItems],
    }))

    const newRow = useCallback(
        (): CalcRequestRow => ({...calcRequestRowInit(), fn: initFrequencyNames}),
        [initFrequencyNames]
    )

    const initCompoundNames = useAsyncResult(makeInvoker({
        func: getCompounds,
        args: [],
        initial: [],
        auxDeps: [localCompoundNames],
    }))

    const initBlendNames = useAsyncResult(makeInvoker({
        func: getBlends,
        args: [],
        initial: [],
        auxDeps: [localBlendNames],
    }))

    const [searchParams, setSearchParams] = useQueryString()

    const initValues = useMemo(
        (): CalcRequestRow[] => {
            try {
                const rows = importRowsFromQueryString(searchParams)
                return rows.length !== 0 ? rows : [newRow()]
            } catch (e) {
                console.log(`query string import failed: ${e}`)
                return [newRow()]
            }
        }, [newRow, searchParams]
    )
    const hydratedInitValues = async (iv: CalcRequestRow[]) => {
        const variantsFetchers = iv.map((row) =>
            row.prefix === '' ? getVariants(row.compoundOrBlend) : toAwaitable(undefined)
        )
        const [
            compounds,
            blends,
            freqs,
            transformerNames,
            transformerFreqs,
            ...variants
        ] = await awaitAllWithBackpressure([
            getCompounds(),
            getBlends(),
            getFrequencies(),
            cachedTransformerNames(),
            cachedTransformerFrequencies(),
            ...variantsFetchers
        ])
        const combinedTransformerFreqs = [...freqs, ...transformerFreqs]

        iv.forEach((row, i) => {
            switch (row.prefix) {
                case '':
                    row.cb = compounds
                    row.vx = variants[i]
                    row.fn = freqs
                    break
                case '.b':
                    row.cb = blends
                    row.fn = freqs
                    break
                case '.t':
                    row.cb = compounds
                    row.vx = transformerNames
                    row.fn = combinedTransformerFreqs
            }
        })
        return iv
    }

    const saveFormToQueryString = (r: CalcRequestRow[]) => {
        setSearchParams(exportRowsToQueryString(r))
    }

    return <>
        <Centered>
            <h1 className="text-2xl">Cycle</h1>
        </Centered>
        <RequireContent
            predicate={initCompoundNames.length > 0 && initFrequencyNames.length > 0}
            errorText='could not load compounds or frequencies'
        >
            <Flex dir="col">
                <MergedCompoundNamesContext value={initCompoundNames}>
                    <MergedBlendNamesContext value={initBlendNames}>
                        <VariantsFetcherContext value={getVariants}>
                            <MergedFrequencyNamesContext value={initFrequencyNames}>
                                <CalcBody
                                    hydratedInitValues={async () => ({cycle: await hydratedInitValues(initValues)})}
                                    newRow={newRow}
                                    localData={{
                                        config: storedConfig,
                                        compounds: localCompounds,
                                        blends: localBlends,
                                        frequencies: localFrequencies,
                                    }}
                                    saveToQS={saveFormToQueryString}
                                />
                            </MergedFrequencyNamesContext>
                        </VariantsFetcherContext>
                    </MergedBlendNamesContext>
                </MergedCompoundNamesContext>
            </Flex>
        </RequireContent>
    </>
}
