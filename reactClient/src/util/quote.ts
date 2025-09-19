export default function quote(s: string): string {
    if (s.indexOf(' ') != -1) return `'${s.replaceAll("'", "\\'")}'`
    return s
}
