export const Button = ({
    type,
    className,
    ...props
}: React.DetailedHTMLProps<
    React.ButtonHTMLAttributes<HTMLButtonElement>,
    HTMLButtonElement
>) => {
    className ??= ''
    type ??= 'button'
    return (
        <button
            className={`bg-gray-400 rounded-xl px-2 ${className}`}
            type={type}
            {...props}
        />
    )
}
