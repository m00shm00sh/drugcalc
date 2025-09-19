import { useState, createContext, useContext, useMemo } from 'react'
import { makeInvoker, useAsyncResult } from '../hooks/useAsyncFetch'
import { useLocalBlends, useLocalCompounds } from '../hooks/useLocalData'
import { Button } from '../widgets/Button'
import memoize from '../util/memoize'
import {
    fetchCompounds,
    fetchVariants,
    memoizedRemoteVariants,
} from '../util/fetcher'
import { selectedItemsFromRemoteFormLoader } from '../util/load-from-remote'
import type {
    BlendEditorComponentRow,
    BlendEditorRow,
    BlendEditorDataContainer,
} from '../types/Blends'
import {
    blendEditorFieldsToMap,
    blendComponentRowInit,
    BlendEditorDataContainerSchema,
    blendRowInit,
    blendMapToEditorFields,
    loadBlendDetailsFromRemote,
} from '../types/Blends'
import type { ByCompoundByVariant } from '../types/Compounds'
import { reshapeCompoundKeys } from '../types/Compounds'
import {
    useForm,
    useFormContext,
    useFieldArray,
    FormProvider,
} from 'react-hook-form'
import type { UseFieldArrayRemove } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { EditorCommands } from './EditorCommands'
import type { EditorProps } from './EditorProps'
import { FormInput, FormSelectWithOptions, FormTextArea } from './FormField'
import { Centered } from '../widgets/Centered'
import { ErrorMessage } from '../widgets/ErrorMessage'
import { Grid, GridCol } from '../widgets/RowCol'
import { getSelectedIndices } from '../types/Selectable'

const LocalCompoundsBcbvContext = createContext<ByCompoundByVariant>({})
const MergedCompoundNamesContext = createContext<string[]>([])
const VariantsFetcherContext = createContext(memoizedRemoteVariants)

type BlendComponentProps = {
    remove: UseFieldArrayRemove
    update: (row: BlendEditorComponentRow) => void
    blendIndex: number
    componentIndex: number
}
const BlendComponent = ({
    blendIndex,
    componentIndex,
    update,
}: BlendComponentProps) => {
    const {
        watch,
        formState: { errors },
    } = useFormContext<BlendEditorDataContainer>()
    const localBcbv = useContext(LocalCompoundsBcbvContext)
    const compounds = useContext(MergedCompoundNamesContext)
    const component = `blends.${blendIndex}.components.${componentIndex}`
    const variantsFetcher = useContext(VariantsFetcherContext)
    const watchRow = watch(`blends.${blendIndex}.components.${componentIndex}`)
    const watchCompound: string = watch(
        `blends.${blendIndex}.components.${componentIndex}.compound`,
    )
    const variants: readonly string[] = useAsyncResult(
        makeInvoker({
            func: variantsFetcher,
            args: [watchCompound],
            initial: [],
            auxDeps: [localBcbv],
        }),
    )
    const componentError =
        errors?.blends?.[blendIndex]?.components?.[componentIndex]
    return (
        <fieldset className={'border ' + (componentError && 'invalid')}>
            <Grid
                auxClasses="col-span-2 place-items-stretch gap-2 p-2"
                colSpec="[fit-content(200px)_auto]"
            >
                <Centered>
                    <FormSelectWithOptions
                        name={`${component}.compound`}
                        optionValues={compounds}
                        onChange={() =>
                            update({ ...watchRow, variant: undefined })
                        }
                    />
                </Centered>
                <Centered>
                    <FormSelectWithOptions
                        name={`${component}.variant`}
                        optionValues={variants}
                    />
                </Centered>
                <Centered>
                    <FormInput
                        placeholder="dose"
                        type="number"
                        name={`${component}.dose`}
                        valueAsNumber
                        min={0.0001}
                        step={0.0001}
                    />
                </Centered>
                <Centered>
                    <FormInput type="checkbox" name={`${component}.selected`} />
                </Centered>
            </Grid>
        </fieldset>
    )
}

type BlendComponentsProps = {
    parentIndex: number
}
const BlendComponents = ({ parentIndex }: BlendComponentsProps) => {
    const {
        control,
        watch,
        formState: { errors },
    } = useFormContext<BlendEditorDataContainer>()
    const { fields, append, remove, update } = useFieldArray({
        control,
        name: `blends.${parentIndex}.components`,
    })
    const componentContainerError = errors?.blends?.[parentIndex]?.components
    const selecteds = getSelectedIndices(
        watch,
        `blends.${parentIndex}.components`,
    )
    return (
        <>
            <div className="grid gap-1">
                <fieldset
                    className={
                        'border ' + (componentContainerError && 'invalid')
                    }
                >
                    <legend>components</legend>
                    {fields.map((field, index) => (
                        <BlendComponent
                            key={field.id}
                            blendIndex={parentIndex}
                            componentIndex={index}
                            remove={() => remove(index)}
                            update={(c: BlendEditorComponentRow) =>
                                update(index, c)
                            }
                        />
                    ))}
                    <Button onClick={() => append(blendComponentRowInit())}>
                        Add component
                    </Button>
                    <Button onClick={() => remove(selecteds)}>
                        Remove selected
                    </Button>
                </fieldset>
                <ErrorMessage text={componentContainerError?.root?.message} />
            </div>
        </>
    )
}

export const BlendsEditor = ({ isLoggedIn }: EditorProps) => {
    const [localCompoundsFromStorage] = useLocalCompounds()
    const localCompoundNames = useMemo(
        () => reshapeCompoundKeys(localCompoundsFromStorage),
        [localCompoundsFromStorage],
    )

    // for useAsyncFetch, auxDeps = [localCompoundNames]
    const getCompounds = memoize(
        async () => await fetchCompounds(localCompoundNames),
        () => '',
        60000,
    )
    // for useAsyncFetch, auxDeps = [localCompoundsFromStorage]
    const getVariants = memoize(
        async (c: string) => await fetchVariants(localCompoundNames, c),
        (s) => s,
        60000,
    )
    const [storage, setStorage] = useLocalBlends()

    const initData = () => {
        if (Object.keys(storage).length > 0)
            return blendMapToEditorFields(storage)
        return [blendRowInit()]
    }

    const methods = useForm<BlendEditorDataContainer>({
        defaultValues: { blends: initData() },
        resolver: zodResolver(BlendEditorDataContainerSchema),
    })
    const { fields, append, remove, update } = useFieldArray({
        control: methods.control,
        name: 'blends',
    })

    const [allowCommit, setAllowCommit] = useState(false)

    const compoundNames = useAsyncResult(
        makeInvoker({
            func: getCompounds,
            args: [],
            initial: [],
            auxDeps: [localCompoundNames],
        }),
    )

    const {
        watch,
        formState: { errors },
    } = methods

    const selecteds = getSelectedIndices(watch, `blends`)

    const loadFromRemote = selectedItemsFromRemoteFormLoader(
        methods,
        'blends',
        loadBlendDetailsFromRemote,
        (i: number) => [`blends.${i}.blend`],
        (r: BlendEditorRow) => ({ ...blendRowInit(), blend: r.blend }),
        update,
    )

    const doSubmit = async (data: BlendEditorRow[]) => {
        console.log(data)
        const map = blendEditorFieldsToMap(data)
        if (allowCommit) throw Error('unimplemented')
        else setStorage(map)
    }

    return (
        <>
            <title>Blends editor</title>
            <Centered>
                <h1 className="text-2xl">Data editor - blends</h1>
            </Centered>
            {compoundNames.length > 0 ? (
                <FormProvider {...methods}>
                    <LocalCompoundsBcbvContext value={localCompoundNames}>
                        <MergedCompoundNamesContext value={compoundNames}>
                            <VariantsFetcherContext value={getVariants}>
                                <form
                                    onSubmit={methods.handleSubmit((e) =>
                                        doSubmit(e.blends),
                                    )}
                                >
                                    {fields.map((field, index) => (
                                        <fieldset
                                            key={field.id}
                                            className={
                                                'border ' +
                                                (errors?.blends?.[index] &&
                                                    'invalid')
                                            }
                                        >
                                            <Grid colSpec={2} auxClasses="gap">
                                                <GridCol auxClasses="gap">
                                                    <FormInput
                                                        name={`blends.${index}.blend`}
                                                        label="name"
                                                        placeholder="name"
                                                    />
                                                    <FormTextArea
                                                        name={`blends.${index}.note`}
                                                        label="note"
                                                        placeholder="note"
                                                    />
                                                    <Centered>
                                                        <FormInput
                                                            type="checkbox"
                                                            name={`blends.${index}.selected`}
                                                        />
                                                    </Centered>
                                                </GridCol>
                                                <BlendComponents
                                                    parentIndex={index}
                                                />
                                            </Grid>
                                        </fieldset>
                                    ))}
                                    <EditorCommands
                                        isLoggedIn={isLoggedIn}
                                        appendRow={() => append(blendRowInit())}
                                        getSelected={() => selecteds}
                                        removeRows={remove}
                                        loadFromRemote={loadFromRemote}
                                        allowCommit={allowCommit}
                                        setAllowCommit={setAllowCommit}
                                    />
                                </form>
                            </VariantsFetcherContext>
                        </MergedCompoundNamesContext>
                    </LocalCompoundsBcbvContext>
                </FormProvider>
            ) : (
                <Centered className="content error-msg text-2xl">
                    <h2>Could not load compounds</h2>
                </Centered>
            )}
        </>
    )
}
