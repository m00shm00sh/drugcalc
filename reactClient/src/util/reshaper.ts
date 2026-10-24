type TryReadonly<V> = V extends number | string | boolean
    ? V
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    : V extends Array<infer _>
        ? ReadonlyArray<V>
        : Readonly<V>

export function fieldsToMap<Row, V>(
    rows: ReadonlyArray<Row>,
    toKey: (_: Row) => string,
    toValue: (_: Row) => V,
): Record<string, V> {
    const kvs = rows.map((e) => [toKey(e), toValue(e)])
    return Object.fromEntries(kvs)
}


export function mapToFields<V, Row>(
    map: Record<string, TryReadonly<V>>,
    fromKey: (key: string) => Partial<Row>,
    fromValue: (value: TryReadonly<V>, currentState: Partial<Row>) => Row,
): Row[] {
    return Object.entries(map).map(([k, v]) => {
        const o: Partial<Row> = fromKey(k)
        return fromValue(v, o)
    })
}
