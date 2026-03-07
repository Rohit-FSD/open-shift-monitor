const Sidebar = () => {
  return (
    <div className="w-64 min-h-screen sticky top-0 bg-slate-800 p-4">
      <h2 className="text-lg font-bold mb-6">OpenShift Monitor</h2>
      <ul className="space-y-3">
        <li>Dashboard</li>
        <li>Services</li>
        <li>Pods</li>
        <li>SLA Metrics</li>
        <li>QA Impact</li>
      </ul>
    </div>
  )
}

export default Sidebar