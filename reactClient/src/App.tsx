import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import { BlendsEditor } from './components/BlendsEditor'
import { CompoundsEditor } from './components/CompoundsEditor'
import { FrequenciesEditor } from './components/FrequenciesEditor'
import { NavBar } from './components/NavBar'

const isLoggedIn = false
const App = () => {
    return (
        <BrowserRouter>
            <NavBar isLoggedIn={isLoggedIn} />
            <Routes>
                <Route path="edit">
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
