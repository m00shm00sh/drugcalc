import type { ReactNode } from 'react'

const ColSpec = {
    'nc-1': 'grid-cols-1',
    'nc-2': 'grid-cols-2',
    'nc-3': 'grid-cols-3',
    'nc-4': 'grid-cols-4',
}

const FlexDir = {
    row: 'flex-row',
    col: 'flex-col',
}

type GridItem = {
    children: ReactNode
    auxClasses?: string
    colSpec: keyof typeof ColSpec
}

type FlexItem = {
    children: ReactNode
    auxClasses?: string
    dir: keyof typeof FlexDir
}

export const Grid = ({ children, auxClasses, colSpec }: GridItem) => {
    auxClasses ??= ''
    return <div className={`grid ${ColSpec[colSpec]} ${auxClasses}`}>
        {children}
    </div>
}

export const Flex = ({ dir, children, auxClasses }: FlexItem) => {
    auxClasses ??= ''
    return (
        <div className={`flex ${FlexDir[dir]} justify-center ${auxClasses}`}>
            {children}
        </div>
    )
}
