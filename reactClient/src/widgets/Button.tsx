import classNames from "classnames"

const ColorClass = {
    'gray-400': 'bg-gray-400',
    'add-item': 'bg-blue-500',
    'remove-item': 'bg-yellow-500',
    'remove-all-items': 'bg-red-500',
    'save-local': 'bg-blue-500',
    'pull-selected-items': 'bg-blue-500',
    'push-selected-items': 'bg-green-500',
    'toggle-commit': 'bg-red-500',
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
