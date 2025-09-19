interface SelectWithOptionsProps
    extends React.DetailedHTMLProps<
        React.SelectHTMLAttributes<HTMLSelectElement>,
        HTMLSelectElement
    > {
    optionValues: string[];
}
export const SelectWithOptions = ({
    optionValues,
    ...props
}: SelectWithOptionsProps) => {
    return (
        <select {...props}>
            {optionValues.map((o, oi) => {
                return (
                    <option key={oi} value={o}>
                        {o}
                    </option>
                )
            })}
        </select>
    )
}
