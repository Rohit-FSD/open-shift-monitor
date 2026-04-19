import { BrowserRouter, Routes, Route } from "react-router-dom"
import DashboardPage from "./pages/DashboardPage"
import FiltersPage from "./pages/FiltersPage"
import SuccessRatePage from "./pages/SuccessRatePage"
import ApplicationFailuresPage from "./pages/ApplicationFailuresPage"
import EnvironmentBookingsPage from "./pages/EnvironmentBookingsPage"

function App() {
  return (
    <BrowserRouter>
      <div className="bg-slate-900 min-h-screen text-white">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/failures" element={<ApplicationFailuresPage />} />
          <Route path="/bookings" element={<EnvironmentBookingsPage />} />
          <Route path="/filters" element={<FiltersPage />} />
          <Route path="/success-rate" element={<SuccessRatePage />} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}

export default App
