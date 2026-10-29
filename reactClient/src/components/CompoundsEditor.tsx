import { useState, type ChangeEvent } from 'react'
import { useLocalStorage } from 'usehooks-ts'
import { Button } from './widgets/Button'
import { COMPOUND_BASE_PATTERN, DURATION_PATTERN } from '../types/Compounds'
import { fetchCompoundDetailsOrNull } from '../util/data-fetcher'
import {
  packCompoundName, quoteCompoundName, unpackCompoundName,
  type CompoundInfo, type CompoundName, type CompoundsMap
} from '../types/Compounds'
import { requireTrue as require, type Nullable } from '../util/util'
import cache from '../util/cache'

// we need this all to be strings because they hold form data; convert appropriate fields to number during form save
type CompoundEditorRowItem = { 
  compound: string
  variant: string
  halflife: string
  pctactive: string,
  note: string
}
function compoundRowInit(): CompoundEditorRowItem {
  return {
    compound: '',
    variant: '',
    halflife: '',
    pctactive: '',
    note: '',
  }
}

const RX_COMPOUND_BASE = RegExp(`^${COMPOUND_BASE_PATTERN}$`)
const RX_DURATION = RegExp(`^${DURATION_PATTERN}$`)
// this function is called during custom validation logic so check for invalid values
function fieldsToCompoundsMap(fields: CompoundEditorRowItem[], failIndex: number[]): CompoundsMap {
  const m: CompoundsMap = {}
  let errorMsg = ''
  const markFail = (idx: number, what: string): void => {
    failIndex.push(idx)
    errorMsg += `${what} at index ${idx}\n`
  }
  for (const [idx, field] of fields.entries()) {
    if (!RX_COMPOUND_BASE.test(field.compound)) {
      markFail(idx, 'invalid compound name')
      continue
    }
    const cv = [field.compound] as CompoundName
    if (field.variant ?? "")
      cv.push(field.variant!)
    const cn = packCompoundName(cv)
    if (m[cn] !== undefined) {
      markFail(idx, `compound name ${quoteCompoundName(cv)} respecified`)
      continue
    }
    if (!RX_DURATION.test(field.halflife)) {
      markFail(idx, 'invalid halflife')
      continue
    }
    const info: CompoundInfo = { halfLife: field.halflife }
    if (field.note)
      info.note = field.note
    if (field.pctactive) {
      const pa = Number(field.pctactive) / 100
      if (pa > 1.0) {
        markFail(idx, 'bioavailability % out of range')
        continue
      }
      if (pa < 0.0) {
        markFail(idx, 'bioavailability % ouf of range')
        continue
      }
      if (pa !== 1.0)
        info.pctActive = pa
    }
    m[cn] = info
  }
  if (errorMsg)
    throw Error(errorMsg)
  return m
}

async function loadCompoundDetailsFromRemote(
  fetcher: (_: CompoundName) => Promise<Nullable<CompoundInfo>>,
  data: CompoundEditorRowItem[],
  failIndex: number[]
): Promise<undefined> {
  let errorMsg = ''
  const markFail = (idx: number, what: string): void => {
    failIndex.push(idx)
    errorMsg += `${what} at index ${idx}\n`
  }
  for (const [idx, elem] of data.entries()) {
      if (elem.compound === "")
        continue
      const cn = [elem.compound] as CompoundName
      if (elem.variant)
        cn.push(elem.variant)
      const info = await fetcher(cn)
      if (info === undefined) {
        markFail(idx, `no details for compound ${quoteCompoundName(cn)}`)
        continue
      }
      elem.halflife = info.halfLife
      if (info.pctActive)
        elem.pctactive = (info.pctActive * 100).toString()
      if (info.note) 
        elem.note = info.note
    }
  if (errorMsg)
    throw Error(errorMsg)
}

function compoundsMapToFields(cm: CompoundsMap): CompoundEditorRowItem[] {
  const a: CompoundEditorRowItem[] = []
  for (const [k, v] of Object.entries(cm)) {
    const o = compoundRowInit()
    const cn = unpackCompoundName(k)
    o.compound = cn[0]
    if (cn[1])
      o.variant = cn[1]
    o.halflife = v.halfLife
    if (v.pctActive)
      o.pctactive = v.pctActive.toString()
    if (v.note)
      o.note = v.note
    a.push(o)
  }
  return a
}

interface CompoundsEditorProps {
  isLoggedIn: boolean
}

export const CompoundsEditor = ({ isLoggedIn } : CompoundsEditorProps) => {
  const [storage, setStorage, ] = useLocalStorage('compounds', {} as CompoundsMap)
  const initFields = () => {
    if (Object.keys(storage).length > 0)
      return compoundsMapToFields(storage)
    return [compoundRowInit()]
  }
  const [fields, setFields] = useState(initFields)
  const [badIndices, setBadIndices] = useState([] as number[])
  const [failMsg, setFailMsg] = useState([] as string[])
  const [allowCommit, setAllowCommit] = useState(false)
  const [allowSave, setAllowSave] = useState(false)

  const fetcher = cache(
    async (cn: CompoundName | undefined) => await fetchCompoundDetailsOrNull(cn as CompoundName),
    (cn: CompoundName): string => packCompoundName(cn),
    60000
  ) as (_: CompoundName) => Promise<CompoundInfo>

  const setAllow = (commit: Nullable<boolean>, save: Nullable<boolean>): void => {
    if (commit !== undefined)
      setAllowCommit(commit)
    if (save !== undefined)
      setAllowSave(save)
  }
  const setErrorStates = (indices: Nullable<number[]>, message: Nullable<string | string[]>): void => {
    if (indices !== undefined)
      setBadIndices(indices)
    if (message !== undefined) {
      if (typeof message !== 'string')
        setFailMsg(message)
      else
        setFailMsg(message.split("\n"))
    }
  }

  const updateRowEntry = (index: number, event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const data = [...fields]
    const target = event.target
    const row = data[index] as { [key: string]: string}
    row[target.name] = target.value
    setAllow(false, undefined)
    setFields(data)
  }

  const addRow = () => {
    const newRow = compoundRowInit()
    setAllow(false, undefined)
    setFields([...fields, newRow])
    setErrorStates([], '')
  }

  const removeRow = (index: number) => {
    const data = [...fields]
    data.splice(index, 1)
    setFields(data)
    setErrorStates([], '')
  }

  const loadFromRemote = async () => {
    setAllow(false, undefined)
    const data = [...fields]
    const bad: number[] = []
    try {
      await loadCompoundDetailsFromRemote(fetcher, data, bad)
      setFields(data)
      setErrorStates(bad, '')
    } catch (e) {
      if (e instanceof Error)
        setErrorStates(bad, e.message)
      else
        throw e
    }
  }

  const checkRows = () => {
    const bad: number[] = []
    try {
      fieldsToCompoundsMap(fields, bad)
      setAllow(undefined, true)
      setErrorStates(bad, '')
    } catch (e) {
      require(bad.length > 0, 'non-validation error')
      require(e instanceof Error, 'unexpected exception type')
      setErrorStates(bad, (e as Error).message)
      setAllow(undefined, false)
    }
  }

  const saveToLocal = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const bad: number[] = []
    try {
      const map = fieldsToCompoundsMap(fields, bad) as CompoundsMap
      setStorage(map)
      setAllow(true, undefined)
      setErrorStates(bad, '')
    } catch {
      require(bad.length > 0, 'non-validation error')
      setErrorStates(bad, '')
    }
  }

  const clearLocal = () => {
    clear()
    setStorage({})
  }

  const commitToRemote = async () => {
    // FIXME: implement
    alert('unimplemented')
    throw Error('unimplemented')
    // POST map /api/compounds { auth: bearer: jwt; content-type: json; body: map }
  }

  const clear = () => {
    setFields([compoundRowInit()])
  }

  return (
    <div id='compound-editor' className='editor'>
      <title>Compounds editor</title>
      <h1>Data editor - compounds</h1>
      <br />
      <form onSubmit={(allowSave) ? saveToLocal : undefined}>
        <table>
          <thead>
            <tr>
              <th></th>
              <th>compound</th>
              <th>variant</th>
              <th>halflife (iso8601-ish)</th>
              <th>bioavailability (%)</th>
              <th>note</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            { fields.map((input, index) => {
              return (
                <tr key={index}
                  className={badIndices.includes(index) ? 'invalid' : undefined}
                >
                  <td>
                    { (fields.length > 1) &&
                      index
                    }
                  </td>
                  <td>
                    <input name='compound' placeholder='compound' pattern={COMPOUND_BASE_PATTERN} minLength={1} required={true}
                      value={input.compound} onChange={e => updateRowEntry(index, e)}
                    />
                  </td>
                  <td>
                    <input name='variant' placeholder='variant'
                      value={input.variant} onChange={e => updateRowEntry(index, e)}
                    />
                  </td>
                  <td>
                    <input name='halflife' placeholder='(iso8601-ish duration)' pattern={DURATION_PATTERN} required={true}
                      value={input.halflife} onChange={e => updateRowEntry(index, e)}
                    />
                  </td>
                  <td>
                    <input name='pctactive' placeholder='% bioavailability' type='number' min={0} max={100} step='any'
                      value={input.pctactive} onChange={e => updateRowEntry(index, e)}
                    />
                  </td>
                  <td>
                    <textarea name='note' placeholder='note'
                      value={input.note} onChange={e => updateRowEntry(index, e)}
                    />
                  </td>
                  <td>
                    { (fields.length > 1) &&
                      <Button type='button' onClick={() => removeRow(index)}>
                        remove
                      </Button>
                    }
                  </td>
                </tr>
              )
            })}
          </tbody>
      </table>
        <div className='grid row'>
          <Button type='button' onClick={addRow}>
            add
          </Button>
          <Button type='button' onClick={clear}>
            clear
          </Button>
        </div>
        <div className='grid row'>
          <Button type='button'onClick={checkRows}>
              check
          </Button>
          <Button type={allowSave ? 'submit' : 'button'} disabled={!allowSave}>
            save
          </Button>
          <Button type='button' onClick={clearLocal}>
            clear local
          </Button>
        </div>
        <div className='grid row'>
          <Button type='button' onClick={loadFromRemote}>
            load from remote
          </Button>
          { (isLoggedIn) &&
            <Button type='button' disabled={!allowCommit} onClick={commitToRemote}>
              commit to remote
            </Button>
          }
        </div>
      </form>
      { (failMsg.length > 0) &&
        <div className='error-msg'>
          <br />
          { failMsg.map((msg, index) => {
            return (
              <p key={index}>{msg}</p>
            )
          })}
        </div>
      }
    </div>
  )
}
