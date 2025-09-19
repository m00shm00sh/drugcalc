export function fieldsToMap<Row, V>(
    rows: Row[],
    toKey: (_: Row) => string,
    toValue: (_: Row) => V,
): Record<string, V> {
    const kvs = rows.map((e) => [toKey(e), toValue(e)])
    return Object.fromEntries(kvs)
}

export function mapToFields<V, Row>(
    map: Record<string, V>,
    fromKey: (key: string) => Partial<Row>,
    fromValue: (value: V, currentState: Partial<Row>) => Row,
): Row[] {
    return Object.entries(map).map(([k, v]) => {
        const o: Partial<Row> = fromKey(k)
        return fromValue(v, o)
    })
}
