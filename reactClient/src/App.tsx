import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import { Calc } from './components/Calc'
import { BlendsEditor } from './components/item-editor/BlendsEditor'
import { CompoundsEditor } from './components/item-editor/CompoundsEditor'
import { FrequenciesEditor } from './components/item-editor/FrequenciesEditor'
import { NavBar } from './components/NavBar'
import { ConfigEditor } from './components/item-editor/ConfigEditor'

const isLoggedIn = false
const App = () => {
    return (
        <BrowserRouter>
            <NavBar isLoggedIn={isLoggedIn} />
            <Routes>
                <Route path="/" element={<Calc />} />
                <Route path="edit">
                    <Route path="config" element={<ConfigEditor />} />
                    <Route
                        path="compounds"
                        element={<CompoundsEditor isLoggedIn={isLoggedIn} />}
                    />
                    <Route
                        path="blends"
                        element={<BlendsEditor isLoggedIn={isLoggedIn} />}
                    />
                    <Route
                        path="frequencies"
                        element={<FrequenciesEditor isLoggedIn={isLoggedIn} />}
                    />
                </Route>
            </Routes>
        </BrowserRouter>
    )
}

export default App
