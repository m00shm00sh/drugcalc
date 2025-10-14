const ColorClass = {
    'gray-400': 'bg-gray-400',
    'sky-400': 'bg-sky-400',
    'amber-400': 'bg-amber-400',
    'red-400': 'bg-red-400',
    'blue-500': 'bg-blue-500',
    'indigo-400': 'bg-indigo-400',
    'emerald-400': 'bg-emerald-400',
    'zinc-600': 'bg-zinc-600'
}

export const Button = ({
    type,
    colorClass,
    className,
    ...props
}: React.DetailedHTMLProps<
    React.ButtonHTMLAttributes<HTMLButtonElement>,
    HTMLButtonElement
> & { colorClass: keyof typeof ColorClass }) => {
    className ??= ''
    type ??= 'button'
    colorClass ??= 'gray-400'
    return (
        <button
            className={`${ColorClass[colorClass]} rounded-xl px-2 ${className}`}
            type={type}
            {...props}
        />
    )
}
