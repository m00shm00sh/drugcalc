import { zodResolver } from '@hookform/resolvers/zod'
import classNames from 'classnames'
import { FormProvider, useFieldArray, useForm } from 'react-hook-form'
import { makeInvoker, useAsyncResult } from '../../hooks/useAsyncFetch'
import type { BlendDeleterDataContainer, BlendDeleterRow } from '../../types/Blends'
import { BlendDeleterDataContainerSchema, blendDeleterRowInit } from '../../types/Blends'
import { getSelectedIndices } from '../../types/Selectable'
import { memoizedRemoteBlends } from '../../util/fetcher'
import { selectedItemsOnRemoteDeleter } from '../../util/remote-load-store'
import { Centered } from '../../widgets/Centered'
import { Flex } from '../../widgets/RowCol'
import type { EditorProps } from '../EditorCommands'
import { EditorCommands } from '../EditorCommands'
import { FormInput, FormSelectWithOptions } from '../FormField'

export const BlendsDeleter = ({ loginToken }: EditorProps) => {
    if (!loginToken)
        throw Error('this component should not rendered without a login token')

    const initData = () => [blendDeleterRowInit()]

    const methods = useForm<BlendDeleterDataContainer>({
        defaultValues: { blends: initData() },
        resolver: zodResolver(BlendDeleterDataContainerSchema),
    })
    const arrayMethods = useFieldArray({
        control: methods.control,
        name: 'blends',
    })
    const { fields, append, remove } = arrayMethods

    const doSubmit = selectedItemsOnRemoteDeleter(
        methods,
        remove,
        'blends',
        (i: number) => [`blends.${i}`],
        '/api/data/blends',
        (r: BlendDeleterRow) => encodeURIComponent(r.blend),
        [memoizedRemoteBlends],
        loginToken,
    )

    const {
        getValues,
        formState: { errors },
    } = methods

    const blendNames = useAsyncResult(makeInvoker({
        func: memoizedRemoteBlends,
        args: [],
        initial: [],
        auxDeps: []
    }))

    const selecteds = getSelectedIndices(getValues, `blends`)
    return (
        <>
            <title>Blends deleter</title>
            <Centered>
                <h1 className="text-2xl">Blends deleter</h1>
            </Centered>
            <FormProvider {...methods}>
                <form onSubmit={methods.handleSubmit((e) => doSubmit(e.blends))}>
                    {fields.map((field, index) => (
                        <fieldset
                            key={field.id}
                            className={classNames(
                                'border',
                                errors?.blends?.[index] && 'invalid'
                            )}
                        >
                            <Flex dir="row" auxClasses={['gap-2', 'p-2']}>
                                <FormInput
                                    name={`blends.${index}.selected`}
                                    type="checkbox"
                                    label=" "
                                />
                                <FormSelectWithOptions
                                    name={`blends.${index}.blend`}
                                    type="text"
                                    optionValues={blendNames}
                                    label="blend"
                                />
                            </Flex>
                        </fieldset>
                    ))}
                    <EditorCommands
                        appendRow={() => append(blendDeleterRowInit())}
                        getSelected={() => selecteds}
                        removeRows={remove}
                        submitStr='Delete from remote'
                        allowCommit={[true, () => {}]}
                    />
                </form>
            </FormProvider>
        </>
    )
}
