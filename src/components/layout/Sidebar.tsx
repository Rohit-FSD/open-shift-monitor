import { useNavigate, useLocation } from "react-router-dom"
import {
  LayoutDashboard,
  Server,
  Boxes,
  BarChart3,
  Activity,
  AlertTriangle,
  Filter,
  TrendingUp
} from "lucide-react"

const Sidebar = () => {

  const navigate = useNavigate()
  const location = useLocation()

  return (

    <div className="w-64 h-screen bg-slate-900 border-r border-slate-800 flex flex-col">

      {/* HEADER */}

      <div className="px-6 py-5 text-lg font-semibold text-white">
        OpenShift Monitor
      </div>

      {/* NAVIGATION */}

      <div className="flex-1 px-4 space-y-1 text-sm">

        <SidebarItem
          icon={<LayoutDashboard size={16} />}
          label="Dashboard"
          active={location.pathname === "/"}
          onClick={() => navigate("/")}
        />

        <SidebarItem icon={<Server size={16} />} label="Services" />

        <SidebarItem icon={<Boxes size={16} />} label="Pods" />

        <SidebarItem icon={<BarChart3 size={16} />} label="SLA Metrics" />

        <SidebarItem icon={<Activity size={16} />} label="QA Impact" />

        <SidebarItem icon={<AlertTriangle size={16} />} label="Alerts" />

        <div className="border-t border-slate-700 my-2" />

        <SidebarItem
          icon={<Filter size={16} />}
          label="Filters"
          active={location.pathname === "/filters"}
          onClick={() => navigate("/filters")}
        />

        <SidebarItem
          icon={<TrendingUp size={16} />}
          label="Success Rate"
          active={location.pathname === "/success-rate"}
          onClick={() => navigate("/success-rate")}
        />

      </div>

      {/* ENVIRONMENT (FIXED AT BOTTOM) */}

      <div className="px-4 py-4 border-t border-slate-800 text-xs text-slate-400">

        <div className="mb-2">Environment</div>

        <div className="bg-slate-800 rounded px-3 py-2 text-white">
          PROD
        </div>

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
    className={`flex items-center gap-3 px-3 py-2 rounded cursor-pointer
    ${active ? "bg-slate-800 text-white" : "text-slate-400 hover:bg-slate-800"}
    `}
    onClick={onClick}
  >

    {icon}

    {label}

  </div>

)

export default Sidebar
