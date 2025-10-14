import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import { Login, Logout } from './components/Auth'
import { Calc } from './components/Calc'
import { BlendsEditor } from './components/item-editor/BlendsEditor'
import { CompoundsEditor } from './components/item-editor/CompoundsEditor'
import { ConfigEditor } from './components/item-editor/ConfigEditor'
import { FrequenciesEditor } from './components/item-editor/FrequenciesEditor'
import { NavBar } from './components/NavBar'
import { useLocalToken } from './hooks/useLocalData'

const App = () => {
    const loginToken = useLocalToken()[0]
    const isLoggedIn = !!loginToken
    return (
        <BrowserRouter>
            <NavBar isLoggedIn={isLoggedIn} />
            <Routes>
                <Route path="/" element={<Calc />} />
                <Route path="edit">
                    <Route path="config" element={<ConfigEditor />} />
                    <Route
                        path="compounds"
                        element={
                            <CompoundsEditor isLoggedIn={isLoggedIn} loginToken={loginToken} />
                        }
                    />
                    <Route path="blends" element={<BlendsEditor isLoggedIn={isLoggedIn} />} />
                    <Route
                        path="frequencies"
                        element={<FrequenciesEditor isLoggedIn={isLoggedIn} />}
                    />
                </Route>
                <Route path="auth">
                    <Route path="login" element={<Login />} />
                    <Route path="logout" element={<Logout />} />
                </Route>
            </Routes>
        </BrowserRouter>
    )
}

export default App
