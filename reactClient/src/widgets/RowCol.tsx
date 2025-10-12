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
    if (nr) nr = ` grid-rows-${nr}`
    return (
        <div
            className={`grid grid-cols-${nc}${nr}${auxClasses}`}
            children={children}
        />
    )
}

type Item = {
    children: ReactNode
    auxClasses?: string
}

export const FlexCol = (props: Item) => <Flex rc={'col'} {...props} />
export const FlexRow = (props: Item) => <Flex rc={'row'} {...props} />

const Flex= ({
    children,
    auxClasses,
    rc,
}: GridItem & {rc: 'col'|'row'}) => {
    auxClasses ??= ''
    if (auxClasses) auxClasses = ' ' + auxClasses
    return (
        <div
            className={`flex flex-${rc} justify-center${auxClasses}`}
            children={children}
        />
    )
}