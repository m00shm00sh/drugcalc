import { zodResolver } from '@hookform/resolvers/zod'
import classNames from 'classnames'
import { FormProvider, useFieldArray, useForm } from 'react-hook-form'
import { makeInvoker, useAsyncResult } from '../../hooks/useAsyncFetch'
import type { FrequencyDeleterDataContainer, FrequencyDeleterRow } from '../../types/Frequencies'
import { FrequencyDeleterDataContainerSchema, frequencyDeleterRowInit } from '../../types/Frequencies'
import { getSelectedIndices } from '../../types/Selectable'
import { fetchFrequencies, memoizedRemoteFrequenciesWithWeights } from '../../util/data-fetcher'
import { selectedItemsOnRemoteDeleter } from '../../util/remote-load-store'
import type { EditorProps } from '../EditorCommands'
import { EditorCommands } from '../EditorCommands'
import { FormInput, FormSelectWithOptions } from '../FormField'
import { Centered } from '../widgets/Centered'
import { RequireContent } from '../widgets/RequireContent'
import { Flex } from '../widgets/RowCol'

export const FrequenciesDeleter = ({ loginToken }: EditorProps) => {
    if (!loginToken)
        throw Error('this component should not rendered without a login token')

    const initData = () => [frequencyDeleterRowInit()]

    const methods = useForm<FrequencyDeleterDataContainer>({
        defaultValues: { frequencies: initData() },
        resolver: zodResolver(FrequencyDeleterDataContainerSchema),
    })
    const arrayMethods = useFieldArray({
        control: methods.control,
        name: 'frequencies',
    })
    const { fields, append, remove } = arrayMethods

    const doSubmit = selectedItemsOnRemoteDeleter(
        methods,
        remove,
        'frequencies',
        (i: number) => [`frequencies.${i}`],
        '/api/data/frequencies',
        (r: FrequencyDeleterRow) => encodeURIComponent(r.frequency),
        [memoizedRemoteFrequenciesWithWeights],
        loginToken,
    )

    const {
        formState: { errors },
        watch,
    } = methods

    const frequencyNames = useAsyncResult(makeInvoker({
        func: async () => await fetchFrequencies({}),
        args: [],
        initial: [],
        auxDeps: []
    }))

    const selecteds = getSelectedIndices(watch, `frequencies`)
    return (
        <>
            <title>Frequencies deleter</title>
            <Centered>
                <h1 className="text-2xl">Frequencies deleter</h1>
            </Centered>
            <RequireContent predicate={frequencyNames.length > 0} errorText='could not load frequencies'>
                <FormProvider {...methods}>
                    <form onSubmit={methods.handleSubmit((e) => doSubmit(e.frequencies))}>
                        {fields.map((field, index) => (
                            <fieldset
                                key={field.id}
                                className={classNames(
                                    'border',
                                    errors?.frequencies?.[index] && 'invalid'
                                )}
                            >
                                <Flex dir="row" auxClasses={['gap-2', 'p-2']}>
                                    <FormInput
                                        name={`frequencies.${index}.selected`}
                                        type="checkbox"
                                        label=" "
                                    />
                                    <FormSelectWithOptions
                                        name={`frequencies.${index}.frequency`}
                                        type="text"
                                        optionValues={frequencyNames}
                                        label="frequency"
                                    />
                                </Flex>
                            </fieldset>
                        ))}
                        <EditorCommands
                            appendRow={() => append(frequencyDeleterRowInit())}
                            getSelected={() => selecteds}
                            removeRows={remove}
                            submitStr='Delete from remote'
                            allowCommit={[true, () => {}]}
                        />
                    </form>
                </FormProvider>
            </RequireContent>
        </>
    )
}
