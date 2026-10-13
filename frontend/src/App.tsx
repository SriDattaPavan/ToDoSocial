import { AuthProvider, useAuth } from './auth/AuthContext'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import './styles.css'

function AppRoutes() {
  const { token } = useAuth()
  return token ? <HomePage /> : <LoginPage />
}

export default function App() {
  return (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  )
}
