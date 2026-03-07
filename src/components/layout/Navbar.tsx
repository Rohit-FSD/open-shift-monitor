const Navbar = () => {
  return (
    <div className="h-16 bg-slate-800 flex items-center justify-between px-6">
      <input
        placeholder="Search service..."
        className="bg-slate-700 px-3 py-2 rounded"
      />
      <div>Last updated: Just now</div>
    </div>
  )
}

export default Navbar