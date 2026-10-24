/// <reference types="@rsbuild/core/types" />

/**
 * Imports the SVG file as a React component.
 * @requires [@rsbuild/plugin-svgr](https://npmjs.com/package/@rsbuild/plugin-svgr)
 */
declare module '*.svg?react' {
    import type React from 'react'
    const ReactComponent: React.FunctionComponent<React.SVGProps<SVGSVGElement>>
    export default ReactComponent
}

/**
 * plotly.js-gl2d-dist-min is a CommonJS bundle without TypeScript declarations.
 * It is loaded dynamically so the bare Node `require()` is not needed in this
 * ESM package; the mock in Calc.test.tsx substitutes it during tests.
 */
declare module 'plotly.js-gl2d-dist-min/' {
    const Plotly: any
    export default Plotly
}
