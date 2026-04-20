import { useNavigate, useLocation } from "react-router-dom"
import {
  LayoutDashboard,
  Calendar,
  FileSearch,
  Filter,
  TrendingUp,
  UserCircle,
} from "lucide-react"
import { useRole, AppRole } from "../../context/RoleContext"

const ROLE_LABELS: Record<AppRole, string> = {
  DEVELOPER: "Developer",
  PROJECT_MANAGER: "Project Manager",
}

const Sidebar = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const { role, setRole, isProjectManager } = useRole()

  const at = (path: string) => location.pathname === path

  const handleRoleChange = (newRole: AppRole) => {
    setRole(newRole)
    // If switching away from PM and on a PM-only page, redirect to dashboard
    if (newRole !== "PROJECT_MANAGER" && (location.pathname === "/filters" || location.pathname === "/success-rate")) {
      navigate("/")
    }
  }

  return (
    <div className="w-64 h-screen bg-slate-900 border-r border-slate-800 flex flex-col">

      <div className="px-6 py-5 text-lg font-semibold text-white">
        OpenShift Monitor
      </div>

      <div className="flex-1 px-4 space-y-1 text-sm overflow-y-auto">

        <SidebarItem icon={<LayoutDashboard size={16} />} label="Dashboard"
          active={at("/")} onClick={() => navigate("/")} />

        <SidebarItem icon={<Calendar size={16} />} label="Environment Bookings"
          active={at("/bookings")} onClick={() => navigate("/bookings")} />

        <SidebarItem icon={<FileSearch size={16} />} label="Journey Logs"
          active={at("/journey-logs")} onClick={() => navigate("/journey-logs")} />

        {isProjectManager && (
          <>
            <div className="border-t border-slate-700 my-2" />

            <div className="px-3 py-1 text-[10px] text-slate-500 uppercase tracking-widest">
              Project Manager
            </div>

            <SidebarItem icon={<Filter size={16} />} label="Filters"
              active={at("/filters")} onClick={() => navigate("/filters")} />

            <SidebarItem icon={<TrendingUp size={16} />} label="Success Rate"
              active={at("/success-rate")} onClick={() => navigate("/success-rate")} />
          </>
        )}

      </div>

      {/* Role picker */}
      <div className="px-4 py-4 border-t border-slate-800 space-y-3">
        <div className="flex items-center gap-2 text-xs text-slate-400">
          <UserCircle size={14} />
          <span>Role</span>
        </div>
        <select
          value={role}
          onChange={e => handleRoleChange(e.target.value as AppRole)}
          className="w-full bg-slate-800 border border-slate-700 rounded px-2 py-1.5 text-xs text-white cursor-pointer"
        >
          {(Object.keys(ROLE_LABELS) as AppRole[]).map(r => (
            <option key={r} value={r}>{ROLE_LABELS[r]}</option>
          ))}
        </select>
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
