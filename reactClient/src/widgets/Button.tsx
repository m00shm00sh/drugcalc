import classNames from "classnames"

const ColorClass = {
    'gray-400': 'bg-gray-400',
    'add-item': 'bg-sky-400',
    'remove-item': 'bg-amber-400',
    'remove-all-items': 'bg-red-400',
    'save-local': 'bg-blue-500',
    'pull-selected-items': 'bg-indigo-400',
    'push-selected-items': 'bg-emerald-400',
    'toggle-commit': 'bg-teal-700',
}

export const Button = ({
    type,
    colorClass,
    className,
    ...props
}: React.DetailedHTMLProps<React.ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement> & {
    colorClass?: keyof typeof ColorClass
}) => {
    type ??= 'button'
    colorClass ??= 'gray-400'
    return (
        <button
            className={classNames(
                ColorClass[colorClass],
                'rounded-xl p-2',
                className?.split(' ')
            )}
            type={type}
            {...props}
        />
    )
}
