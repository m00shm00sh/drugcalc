import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import type { UseFieldArrayRemove } from 'react-hook-form'
import {
    FormProvider,
    useFieldArray,
    useForm,
    useFormContext,
} from 'react-hook-form'
import { useLocalFrequencies } from '../../hooks/useLocalData'
import type {
    FrequencyEditorDataContainer,
    FrequencyEditorRow,
} from '../../types/Frequencies'
import {
    frequencyEditorComponentItemInit,
    FrequencyEditorDataContainerSchema,
    frequencyEditorFieldsToMap,
    frequencyEditorRowInit,
    frequencyMapToEditorFields,
    loadFrequencyDetailsFromRemote,
} from '../../types/Frequencies'
import { getSelectedIndices } from '../../types/Selectable'
import { selectedItemsFromRemoteFormLoader } from '../../util/load-from-remote'
import { Button } from '../../widgets/Button'
import { Centered } from '../../widgets/Centered'
import { ErrorMessage } from '../../widgets/ErrorMessage'
import { FlexRow, GridCol } from '../../widgets/RowCol'
import { EditorCommands } from '../EditorCommands'
import type { EditorProps } from '../EditorProps'
import { FormInput } from '../FormField'

type FrequencyComponentProps = {
    remove: UseFieldArrayRemove
    frequencyIndex: number
    componentIndex: number
}
const FrequencyComponent = ({
    frequencyIndex,
    componentIndex,
}: FrequencyComponentProps) => {
    const component = `frequencies.${frequencyIndex}.values.${componentIndex}`
    return (
        <fieldset className="gap-2 border flex flex-row">
            <FormInput
                type="checkbox"
                name={`${component}.selected`}
            />
            <FormInput
                type="text"
                placeholder="iso8601-ish"
                name={`${component}.value`}
            />
        </fieldset>
    )
}

type FrequencyComponentsProps = {
    parentIndex: number
}
const FrequencyComponents = ({ parentIndex }: FrequencyComponentsProps) => {
    const {
        control,
        watch,
        formState: { errors },
    } = useFormContext<FrequencyEditorDataContainer>()
    const { fields, append, remove } = useFieldArray({
        control,
        name: `frequencies.${parentIndex}.values`,
    })
    const componentContainerError = errors?.frequencies?.[parentIndex]?.values
    const selecteds = getSelectedIndices(
        watch,
        `frequencies.${parentIndex}.values`,
    )
    return (
        <fieldset
            className={`border ${componentContainerError && 'invalid'}`}
        >
            <legend>frequencies</legend>
            <GridCol auxClasses="gap-2 p-2">
                {fields.map((field, index) => (
                    <FrequencyComponent
                        key={field.id}
                        frequencyIndex={parentIndex}
                        componentIndex={index}
                        remove={remove}
                    />
                ))}
                <FlexRow>
                    <Button
                        onClick={() => append(frequencyEditorComponentItemInit())}
                    >
                        Add component
                    </Button>
                    <Button onClick={() => remove(selecteds)}>
                        Remove selected
                    </Button>
                </FlexRow>
                <ErrorMessage text={componentContainerError?.root?.message} />
            </GridCol>
        </fieldset>
    )
}

export const FrequenciesEditor = ({ isLoggedIn }: EditorProps) => {
    const [storage, setStorage] = useLocalFrequencies()

    const initData = () => {
        if (Object.keys(storage).length > 0)
            return frequencyMapToEditorFields(storage)
        return [frequencyEditorRowInit()]
    }

    const methods = useForm<FrequencyEditorDataContainer>({
        defaultValues: { frequencies: initData() },
        resolver: zodResolver(FrequencyEditorDataContainerSchema),
    })
    const { fields, append, remove, update } = useFieldArray({
        control: methods.control,
        name: 'frequencies',
    })

    const [allowCommit, setAllowCommit] = useState(false)

    const loadFromRemote = selectedItemsFromRemoteFormLoader(
        methods,
        'frequencies',
        loadFrequencyDetailsFromRemote,
        (i: number) => [`frequencies.${i}.frequency`],
        (r: FrequencyEditorRow) => ({
            ...frequencyEditorRowInit(),
            frequency: r.frequency,
        }),
        update,
    )

    const doSubmit = async (data: FrequencyEditorRow[]) => {
        console.log(data)
        const map = frequencyEditorFieldsToMap(data)
        if (allowCommit) throw Error('unimplemented')
        else {
            setStorage(map)
        }
    }

    const {
        formState: { errors },
        watch,
    } = methods
    const selecteds = getSelectedIndices(watch, `frequencies`)
    return (
        <>
            <title>Frequencies editor</title>
            <Centered>
                <h1 className="text-2xl">Data editor - frequencies</h1>
            </Centered>
            <FormProvider {...methods}>
                <form
                    onSubmit={methods.handleSubmit((e) =>
                        doSubmit(e.frequencies),
                    )}
                >
                    {fields.map((field, index) => (
                        <fieldset
                            key={field.id}
                            className={
                                'border ' +
                                (errors?.frequencies?.[index] && 'invalid')
                            }
                        >
                            <FlexRow auxClasses="gap">
                                <FormInput
                                    type="checkbox"
                                    name={`frequencies.${index}.selected`}
                                />
                                <FormInput
                                    name={`frequencies.${index}.frequency`}
                                    type="text"
                                    label="name"
                                    placeholder="name"
                                />
                                <FrequencyComponents parentIndex={index} />
                            </FlexRow>
                        </fieldset>
                    ))}
                    <EditorCommands
                        isLoggedIn={isLoggedIn}
                        appendRow={() => append(frequencyEditorRowInit())}
                        getSelected={() => selecteds}
                        removeRows={remove}
                        loadFromRemote={loadFromRemote}
                        allowCommit={[allowCommit, setAllowCommit]}
                    />
                </form>
            </FormProvider>
        </>
    )
}
