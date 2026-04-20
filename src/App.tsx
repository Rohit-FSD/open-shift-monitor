import { BrowserRouter, Routes, Route } from "react-router-dom"
import DashboardPage from "./pages/DashboardPage"
import FiltersPage from "./pages/FiltersPage"
import SuccessRatePage from "./pages/SuccessRatePage"
import EnvironmentBookingsPage from "./pages/EnvironmentBookingsPage"
import JourneyLogsPage from "./pages/JourneyLogsPage"

function App() {
  return (
    <BrowserRouter>
      <div className="bg-slate-900 min-h-screen text-white">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/bookings" element={<EnvironmentBookingsPage />} />
          <Route path="/journey-logs" element={<JourneyLogsPage />} />
          <Route path="/filters" element={<FiltersPage />} />
          <Route path="/success-rate" element={<SuccessRatePage />} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}

export default App
