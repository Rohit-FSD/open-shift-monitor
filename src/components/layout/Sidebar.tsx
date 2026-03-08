import {
  LayoutDashboard,
  Server,
  Boxes,
  BarChart3,
  Activity,
  AlertTriangle
} from "lucide-react"

const Sidebar = () => {

  return (

    <div className="w-64 bg-slate-900 border-r border-slate-800 flex flex-col">

      <div className="px-6 py-5 text-lg font-semibold">
        OpenShift Monitor
      </div>

      <div className="flex-1 px-4 space-y-1 text-sm">

        <SidebarItem icon={<LayoutDashboard size={16} />} label="Dashboard" active />

        <SidebarItem icon={<Server size={16} />} label="Services" />

        <SidebarItem icon={<Boxes size={16} />} label="Pods" />

        <SidebarItem icon={<BarChart3 size={16} />} label="SLA Metrics" />

        <SidebarItem icon={<Activity size={16} />} label="QA Impact" />

        <SidebarItem icon={<AlertTriangle size={16} />} label="Alerts" />

      </div>

      <div className="px-4 py-4 border-t border-slate-800 text-xs text-slate-400">

        <div className="mb-2">Environment</div>

        <div className="bg-slate-800 rounded px-3 py-2">
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

}

const SidebarItem = ({ icon, label, active }: ItemProps) => (

  <div
    className={`flex items-center gap-3 px-3 py-2 rounded cursor-pointer
    ${active ? "bg-slate-800 text-white" : "text-slate-400 hover:bg-slate-800"}
    `}
  >

    {icon}

    {label}

  </div>

)

export default Sidebar