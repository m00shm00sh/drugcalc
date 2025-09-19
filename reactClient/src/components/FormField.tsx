import type { ReactNode } from 'react'
import type { FieldError, FieldPath, FieldValues } from 'react-hook-form'
import { useFormContext } from 'react-hook-form'
import { Column } from '../widgets/RowCol'

export type FieldProps<T> = {
    type?: string;
    placeholder: string;
    name: FieldPath<T & FieldValues>;
    valueAsNumber?: boolean;
};

function getChain<R>(o: unknown, ks: string): R | undefined {
    for (const k of ks.split('.')) {
        if (o === undefined) return undefined
        const k_N = Number(k)
        if (!isNaN(k_N)) {
            const o_ = o as Array<unknown>
            o = o_[k_N]
            continue
        }
        const o_ = o as Record<string, unknown>
        o = o_[k]
    }
    return o as R | undefined
}

export function FormInput<T>({
    type,
    placeholder,
    name,
    valueAsNumber,
}: FieldProps<T>): ReactNode {
    const {
        register,
        formState: { errors },
    } = useFormContext()
    const errorValue = getChain<FieldError>(errors, name)
    return (
        <Column grid>
            {placeholder && <label>{placeholder}</label>}
            <input
                type={type}
                placeholder={placeholder}
                {...register(name, { valueAsNumber })}
            />
            {errorValue && <div className="error-msg">{errorValue.message}</div>}
        </Column>
    )
}

export function FormSelectWithOptions<T>({
    placeholder,
    name,
    valueAsNumber,
    optionValues,
}: FieldProps<T> & { optionValues: readonly string[] }): ReactNode {
    const {
        register,
        formState: { errors },
    } = useFormContext()
    const errorValue = getChain<FieldError>(errors, name)
    return (
        <>
            {optionValues.length > 0 && (
                <Column grid>
                    <label>{placeholder}</label>
                    <select {...register(name, { valueAsNumber })}>
                        {optionValues.map((ov, oi) => {
                            return (
                                <option key={oi} value={ov}>
                                    {ov}
                                </option>
                            )
                        })}
                    </select>
                    {errorValue && <div className="error-msg">{errorValue.message}</div>}
                </Column>
            )}
        </>
    )
}

export function FormTextArea<T>({
    placeholder,
    name,
}: FieldProps<T>): ReactNode {
    const {
        register,
        formState: { errors },
    } = useFormContext()
    const errorValue = getChain<FieldError>(errors, name)
    return (
        <Column grid>
            <label>{placeholder}</label>
            <textarea placeholder={placeholder} {...register(name)} />
            {errorValue && <div className="error-msg">{errorValue.message}</div>}
        </Column>
    )
}
