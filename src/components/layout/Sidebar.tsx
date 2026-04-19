import { useNavigate, useLocation } from "react-router-dom"
import {
  LayoutDashboard,
  Activity,
  AlertTriangle,
  AlertCircle,
  Calendar,
  Filter,
  TrendingUp,
} from "lucide-react"

const Sidebar = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const at = (path: string) => location.pathname === path

  return (
    <div className="w-64 h-screen bg-slate-900 border-r border-slate-800 flex flex-col">

      <div className="px-6 py-5 text-lg font-semibold text-white">
        OpenShift Monitor
      </div>

      <div className="flex-1 px-4 space-y-1 text-sm overflow-y-auto">

        <SidebarItem icon={<LayoutDashboard size={16} />} label="Dashboard"
          active={at("/")} onClick={() => navigate("/")} />

        <SidebarItem icon={<Activity size={16} />} label="QA Impact"
          active={at("/qa-impact")} onClick={() => navigate("/qa-impact")} />

        <SidebarItem icon={<AlertTriangle size={16} />} label="Alerts"
          active={at("/alerts")} onClick={() => navigate("/alerts")} />

        <SidebarItem icon={<AlertCircle size={16} />} label="Application Failures"
          active={at("/failures")} onClick={() => navigate("/failures")} />

        <SidebarItem icon={<Calendar size={16} />} label="Environment Bookings"
          active={at("/bookings")} onClick={() => navigate("/bookings")} />

        <div className="border-t border-slate-700 my-2" />

        <SidebarItem icon={<Filter size={16} />} label="Filters"
          active={at("/filters")} onClick={() => navigate("/filters")} />

        <SidebarItem icon={<TrendingUp size={16} />} label="Success Rate"
          active={at("/success-rate")} onClick={() => navigate("/success-rate")} />

      </div>

      <div className="px-4 py-4 border-t border-slate-800 text-xs text-slate-400">
        <div className="mb-2">Environment</div>
        <div className="bg-slate-800 rounded px-3 py-2 text-white">PROD</div>
      </div>

    </div>
  )
}

interface ItemProps {
  icon: React.ReactNode
  label: string
  active?: boolean
  onClick?: () => void
}

const SidebarItem = ({ icon, label, active, onClick }: ItemProps) => (
  <div
    className={`flex items-center gap-3 px-3 py-2 rounded cursor-pointer transition-colors
      ${active ? "bg-slate-800 text-white" : "text-slate-400 hover:bg-slate-800 hover:text-white"}`}
    onClick={onClick}
  >
    {icon}
    {label}
  </div>
)

export default Sidebar
