// Stub for CSS modules so Jest doesn't try to parse CSS as JavaScript.
// Exposes every class name as itself, so className-based assertions keep working.
export default new Proxy({}, {
    get(_, prop) {
        return prop
    },
})
