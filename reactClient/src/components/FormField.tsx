import classNames from 'classnames'
import type { ReactElement, SyntheticEvent } from 'react'
import type { FieldError, FieldPath, FieldValues } from 'react-hook-form'
import { useFormContext } from 'react-hook-form'
import type { Nullable } from '../util/util'
import { ErrorMessage } from '../widgets/ErrorMessage'
import { Flex } from '../widgets/RowCol'

export type FieldProps<T extends FieldValues, E extends Element> = {
    type?: string
    label?: string
    name: FieldPath<T>
    blockClasses?: readonly string[]
    inpClasses?: readonly string[]
    onChange?: (event: SyntheticEvent<E>) => void
}

type FieldHasPlaceholder = { placeholder: string }

export type FieldPropsText<T extends FieldValues, E extends Element> = FieldProps<T, E> & {
    type: 'text' | 'password'
    minLength?: number
    maxLength?: number
} & FieldHasPlaceholder

export type FieldPropsNumeric<T extends FieldValues, E extends Element> = FieldProps<T, E> & {
    type: 'number' | 'range'
    min?: number
    max?: number
    step?: number
} & FieldHasPlaceholder

type FieldPropsAny<T extends FieldValues, E extends Element> =
    | FieldProps<T, E>
    | FieldPropsText<T, E>
    | FieldPropsNumeric<T, E>

function getChain<R>(o: unknown, ks: string): Nullable<R> {
    for (const k of ks.split('.')) {
        if (o === undefined) return undefined
        const k_N = Number(k)
        if (!Number.isNaN(k_N)) {
            const o_ = o as Array<unknown>
            o = o_[k_N]
            continue
        }
        const o_ = o as Record<string, unknown>
        o = o_[k]
    }
    return o as R | undefined
}

const OptionalLabel = ({ label }: { label: Nullable<string> }) => {
    if (!label) return
    return <label>{label}</label>
}

export function FormInput<T extends FieldValues>({
    blockClasses,
    inpClasses,
    type,
    label,
    name,
    onChange,
    ...props
}: FieldPropsAny<T, HTMLInputElement>): ReactElement {
    const {
        register,
        formState: { errors },
    } = useFormContext<T>()
    const errorValue = getChain<FieldError>(errors, name)
    const inputClasses = [...(inpClasses ?? [])]
    let placeholder: Nullable<string>
    const valueAsNumber = type === 'number'
    if (['text', 'password', 'number'].indexOf(type ?? '') >= 0 && 'placeholder' in props) {
        placeholder = props.placeholder
        inputClasses.push('indent-2')
    }
    return (
        <Flex dir="col" auxClasses={blockClasses}>
            <OptionalLabel label={label} />
            <input
                className={classNames(
                    'bg-gray-400 text-gray-900',
                    'focus:ring',
                    'w-min resize-x p-1',
                    ...inputClasses
                )}
                type={type}
                placeholder={placeholder}
                {...register(name, {
                    onChange,
                    valueAsNumber: valueAsNumber,
                    ...props,
                })}
                {...props}
            />
            {type !== 'checkbox' && <ErrorMessage text={errorValue?.message} />}
        </Flex>
    )
}

type OptionValues = readonly string[] | Record<string, string>
const OptionValues = ({ optionValues }: { optionValues: OptionValues }): ReactElement => {
    if (Array.isArray(optionValues))
        return (
            <>
                {optionValues.map((ov) => (
                    <option key={ov} value={ov}>
                        {ov}
                    </option>
                ))}
            </>
        )
    return (
        <>
            {Object.entries(optionValues).map(([ok, ov]) => (
                <option key={ov} value={ov}>
                    {ok}
                </option>
            ))}
        </>
    )
}

export function FormSelectWithOptions<T extends FieldValues>({
    blockClasses,
    inpClasses,
    label,
    name,
    onChange,
    defaultValue,
    optionValues,
}: FieldProps<T, HTMLSelectElement> & {
    defaultValue?: string
    optionValues: OptionValues
}): Nullable<ReactElement> {
    const {
        register,
        formState: { errors },
    } = useFormContext<T>()
    const inputClasses = [...(inpClasses ?? [])]
    if (Array.isArray(optionValues) && optionValues.length < 1) return
    if (Object.keys(optionValues).length < 1) return
    const errorValue = getChain<FieldError>(errors, name)
    return (
        <Flex dir="col" auxClasses={blockClasses}>
            <OptionalLabel label={label} />
            <select
                className={classNames(
                    'bg-gray-400 text-gray-900',
                    'focus:ring',
                    'p-1 resize-x align-middle',
                    ...inputClasses
                )}
                {...(defaultValue && { defaultValue: defaultValue })}
                {...register(name, { onChange })}
            >
                <OptionValues optionValues={optionValues} />
            </select>
            <ErrorMessage text={errorValue?.message} />
        </Flex>
    )
}

export function FormTextArea<T extends FieldValues>({
    blockClasses,
    inpClasses,
    placeholder,
    label,
    name,
    onChange,
}: FieldProps<T, HTMLTextAreaElement> & Partial<FieldHasPlaceholder>): ReactElement {
    const {
        register,
        formState: { errors },
    } = useFormContext<T>()
    const inputClasses = [...(inpClasses ?? [])]
    const errorValue = getChain<FieldError>(errors, name)
    return (
        <Flex dir="col" auxClasses={blockClasses}>
            <OptionalLabel label={label} />
            <textarea
                className={classNames(
                    'bg-gray-400 text-gray-900',
                    'focus:ring',
                    'p-2 resize-x',
                    ...inputClasses
                )}
                placeholder={placeholder}
                {...register(name, { onChange })}
            />
            <ErrorMessage text={errorValue?.message} />
        </Flex>
    )
}
