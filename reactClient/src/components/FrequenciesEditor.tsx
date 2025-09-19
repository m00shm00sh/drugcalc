import { useState } from 'react'
import { useLocalFrequencies } from '../hooks/useLocalData'
import { Button } from '../widgets/Button'
import { Column, Row } from '../widgets/RowCol'
import memoize from '../util/memoize'
import { fetchFrequencyDetailsOrNull } from '../util/fetcher'
import { itemsFromRemoteFormLoader } from '../util/load-from-remote'
import { fieldsToMap, mapToFields } from '../util/reshaper'
import { type Nullable } from '../util/util'
import type {
    FrequenciesMap,
    FrequencyEntry,
    FrequencyEditorComponentItem,
    FrequencyEditorRow,
    FrequencyEditorDataContainer,
} from '../types/Frequencies'
import {
    frequencyEditorComponentItemInit,
    frequencyEditorRowInit,
    FrequencyEditorDataContainerSchema,
} from '../types/Frequencies'
import { displayToIso8601, iso8601ToDisplay } from '../types/duration'
import {
    useForm,
    useFormContext,
    useFieldArray,
    FormProvider,
} from 'react-hook-form'
import type { UseFieldArrayRemove } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import type { EditorProps } from './EditorProps'
import { EditorCommands } from './EditorCommands'
import { FormInput } from './FormField'

const componentFieldsToEntry = (
    fields: FrequencyEditorComponentItem[],
): FrequencyEntry => ({ values: fields.map((e) => displayToIso8601(e.value)) })

const fFieldsToMap = (fields: FrequencyEditorRow[]): FrequenciesMap =>
    fieldsToMap(
        fields,
        (r) => r.frequency,
        (r) => componentFieldsToEntry(r.values),
    )

const entryToComponentFields = (
    entry: FrequencyEntry,
): FrequencyEditorComponentItem[] =>
    entry.values.map(
        (e) => ({ value: iso8601ToDisplay(e) }) as FrequencyEditorComponentItem,
    )

const fMapToFields = (map: FrequenciesMap): FrequencyEditorRow[] =>
    mapToFields(
        map,
        (k) => ({ frequency: k }) as Partial<FrequencyEditorRow>,
        (v, o) => {
            o.values = entryToComponentFields(v)
            return o as FrequencyEditorRow
        },
    )

const fetchDetails = memoize(
    async (f: string) => await fetchFrequencyDetailsOrNull(f),
    (s) => s,
    60000,
)

const loadDetailsFromRemote = async (
    row: FrequencyEditorRow,
): Promise<Nullable<FrequencyEditorRow>> =>
    fetchDetails(row.frequency).then(
        (f?: FrequencyEntry): Nullable<FrequencyEditorRow> => {
            if (!f) return undefined
            const o: FrequencyEditorRow = {
                ...row,
                values: entryToComponentFields(f),
            }
            return o
        },
    )

type FrequencyComponentProps = {
    remove: UseFieldArrayRemove;
    frequencyIndex: number;
    componentIndex: number;
};
const FrequencyComponent = ({
    frequencyIndex,
    componentIndex,
    remove,
}: FrequencyComponentProps) => {
    const component = `frequencies.${frequencyIndex}.values.${componentIndex}`
    return (
        <Column grid>
            <FormInput placeholder="" name={`${component}.value`} />
            <Button type="button" onClick={() => remove(componentIndex)}>
                Remove
            </Button>
        </Column>
    )
}

type FrequencyComponentsProps = {
    parentIndex: number;
};
const FrequencyComponents = ({ parentIndex }: FrequencyComponentsProps) => {
    const {
        control,
        formState: { errors },
    } = useFormContext<FrequencyEditorDataContainer>()
    const { fields, append, remove } = useFieldArray({
        control,
        name: `frequencies.${parentIndex}.values`,
    })
    const componentContainerError = errors?.frequencies?.[parentIndex]?.values
    return (
        <Row grid>
            <fieldset className={componentContainerError && 'invalid'}>
                <legend>frequencies</legend>
                {fields.map((field, index) => (
                    <FrequencyComponent
                        key={field.id}
                        frequencyIndex={parentIndex}
                        componentIndex={index}
                        remove={remove}
                    />
                ))}
                <Button
                    type="button"
                    onClick={() => append(frequencyEditorComponentItemInit())}
                >
                    Add component
                </Button>
            </fieldset>
            {componentContainerError?.root?.message && (
                <div className="error-msg">
                    {componentContainerError?.root?.message}
                </div>
            )}
        </Row>
    )
}

export const FrequenciesEditor = ({ isLoggedIn }: EditorProps) => {
    const [storage, setStorage] = useLocalFrequencies()

    const initData = () => {
        if (Object.keys(storage).length > 0) return fMapToFields(storage)
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

    const loadFromRemote = itemsFromRemoteFormLoader(
        methods,
        'frequencies',
        loadDetailsFromRemote,
        (r: FrequencyEditorRow) => !r.frequency,
        (i: number) => [`frequencies.${i}.frequency`],
        (r: FrequencyEditorRow) => ({
            ...frequencyEditorRowInit(),
            frequency: r.frequency,
        }),
        update,
    )

    const doSubmit = async (data: FrequencyEditorRow[]) => {
        console.log(data)
        const map = fFieldsToMap(data)
        if (allowCommit) throw Error('unimplemented')
        else {
            setStorage(map)
        }
    }

    const {
        formState: { errors },
    } = methods
    return (
        <>
            <title>Frequencies editor</title>
            <FormProvider {...methods}>
                <form onSubmit={methods.handleSubmit((e) => doSubmit(e.frequencies))}>
                    <h1>Data editor - frequencies</h1>
                    <Column grid>
                        {fields.map((field, index) => (
                            <fieldset
                                key={field.id}
                                className={errors?.frequencies?.[index] && 'invalid'}
                            >
                                <Row grid>
                                    <Column grid>
                                        <FormInput
                                            name={`frequencies.${index}.frequency`}
                                            placeholder="name"
                                        />
                                        <Button type="button" onClick={() => remove(index)}>
                                            Remove frequency
                                        </Button>
                                    </Column>
                                    <FrequencyComponents parentIndex={index} />
                                </Row>
                            </fieldset>
                        ))}
                    </Column>
                    <EditorCommands
                        isLoggedIn={isLoggedIn}
                        appendRow={() => append(frequencyEditorRowInit())}
                        removeAll={remove}
                        loadFromRemote={loadFromRemote}
                        allowCommit={allowCommit}
                        setAllowCommit={setAllowCommit}
                    />
                </form>
            </FormProvider>
        </>
    )
}
