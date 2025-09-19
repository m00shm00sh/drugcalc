import type { ReactNode } from 'react'

type GridItem = {
    children: ReactNode
    auxClasses?: string
    colSpec?: number | string
    rowSpec?: number | string
}

export const GridCol = (props: GridItem) => <Grid {...props} colSpec={1} />

export const Grid = ({
    children,
    auxClasses,
    colSpec: nc,
    rowSpec: nr,
}: GridItem) => {
    auxClasses ??= ''
    if (auxClasses) auxClasses = ' ' + auxClasses
    nr ??= ''
    if (nr) nr = ` grid-cols-${nr}`
    return (
        <div
            className={`grid grid-cols-${nc}${nr}${auxClasses}`}
            children={children}
        />
    )
}
