import { useState } from "react"
import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import useFetch from "../hooks/useFetch"
import { Plus, X, Calendar, User, Tag, FileText, Clock } from "lucide-react"

interface Booking {
  id?: number
  environmentName: string
  teamName: string
  bookedBy: string
  jiraTicket: string
  purpose: string
  releaseAt: string
  status?: string
  createdAt?: string
}

const statusColor: Record<string, string> = {
  ACTIVE: "bg-green-600",
  UPCOMING: "bg-blue-600",
  RELEASED: "bg-slate-600",
  EXPIRED: "bg-red-700",
}

const EnvironmentBookingsPage = () => {
  const [showForm, setShowForm] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const { data: bookings, refetch } = useFetch<Booking[]>("http://localhost:8080/api/environment-booking/all")

  const defaultForm: Booking = {
    environmentName: "",
    teamName: "",
    bookedBy: "",
    jiraTicket: "",
    purpose: "",
    releaseAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 16),
  }

  const [form, setForm] = useState<Booking>(defaultForm)

  const handleChange = (field: keyof Booking, value: string) =>
    setForm(prev => ({ ...prev, [field]: value }))

  const handleSubmit = async () => {
    if (!form.environmentName || !form.teamName || !form.bookedBy || !form.jiraTicket || !form.purpose) {
      setSubmitError("Please fill in all required fields")
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      const res = await fetch("http://localhost:8080/api/environment-booking/create", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setForm(defaultForm)
      setShowForm(false)
      refetch()
    } catch (e: any) {
      setSubmitError(e.message ?? "Failed to create booking")
    } finally {
      setSubmitting(false)
    }
  }

  const list: Booking[] = Array.isArray(bookings) ? bookings : []

  return (
    <div className="flex min-h-screen bg-slate-950 text-white">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        <Navbar />
        <div className="p-6 max-w-5xl mx-auto w-full">

          {/* Header */}
          <div className="flex items-center justify-between mb-6">
            <h1 className="text-2xl font-semibold text-white">Environment Bookings</h1>
            <div className="flex gap-2">
              <button
                onClick={() => refetch()}
                className="flex items-center gap-1 bg-slate-700 hover:bg-slate-600 text-sm px-3 py-2 rounded"
              >
                Refresh
              </button>
              <button
                onClick={() => { setShowForm(true); setSubmitError(null) }}
                className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-sm px-4 py-2 rounded"
              >
                <Plus size={14} /> New Booking
              </button>
            </div>
          </div>

          {/* Create form */}
          {showForm && (
            <div className="bg-slate-800 rounded-lg p-6 mb-6 border border-slate-700">
              <div className="flex justify-between items-center mb-5">
                <h2 className="text-lg font-medium text-white">Create New Booking</h2>
                <button onClick={() => setShowForm(false)} className="text-slate-400 hover:text-white">
                  <X size={18} />
                </button>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Environment Name *</label>
                  <input
                    className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-white placeholder-slate-500"
                    placeholder="e.g., QA01, DEV02, PROD"
                    value={form.environmentName}
                    onChange={e => handleChange("environmentName", e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Team Name *</label>
                  <input
                    className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-white placeholder-slate-500"
                    placeholder="e.g., KTLO Team"
                    value={form.teamName}
                    onChange={e => handleChange("teamName", e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Booked By (Email) *</label>
                  <input
                    type="email"
                    className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-white placeholder-slate-500"
                    placeholder="john.doe@company.com"
                    value={form.bookedBy}
                    onChange={e => handleChange("bookedBy", e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">JIRA Ticket *</label>
                  <input
                    className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-white placeholder-slate-500"
                    placeholder="PROJ-1234"
                    value={form.jiraTicket}
                    onChange={e => handleChange("jiraTicket", e.target.value)}
                  />
                </div>
                <div className="col-span-2">
                  <label className="block text-xs text-slate-400 mb-1">Purpose *</label>
                  <textarea
                    rows={3}
                    className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-white placeholder-slate-500 resize-none"
                    placeholder="e.g., Performance testing, Bug fixes, Feature deployment"
                    value={form.purpose}
                    onChange={e => handleChange("purpose", e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Release At *</label>
                  <input
                    type="datetime-local"
                    className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-white"
                    value={form.releaseAt}
                    onChange={e => handleChange("releaseAt", e.target.value)}
                  />
                </div>
              </div>

              {submitError && (
                <p className="mt-3 text-sm text-red-400">{submitError}</p>
              )}

              <div className="flex justify-end gap-3 mt-5">
                <button
                  onClick={() => setShowForm(false)}
                  className="px-4 py-2 text-sm text-slate-300 hover:text-white bg-slate-700 rounded"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSubmit}
                  disabled={submitting}
                  className="px-4 py-2 text-sm bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded"
                >
                  {submitting ? "Creating..." : "Create Booking"}
                </button>
              </div>
            </div>
          )}

          {/* Bookings list */}
          <div className="bg-slate-800 rounded-lg">
            <div className="px-5 py-4 border-b border-slate-700">
              <h2 className="text-base font-medium text-white">All Bookings</h2>
            </div>
            {list.length === 0 ? (
              <div className="p-8 text-center text-slate-400">
                <Calendar size={36} className="mx-auto mb-3 text-slate-600" />
                <p>No bookings found</p>
              </div>
            ) : (
              <div className="divide-y divide-slate-700">
                {list.map((b, i) => (
                  <div key={b.id ?? i} className="px-5 py-4 flex flex-wrap gap-4 items-start">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-white font-medium">{b.environmentName}</span>
                        {b.status && (
                          <span className={`text-xs px-2 py-0.5 rounded-full text-white ${statusColor[b.status] ?? "bg-slate-600"}`}>
                            {b.status}
                          </span>
                        )}
                      </div>
                      <div className="flex flex-wrap gap-3 text-xs text-slate-400">
                        <span className="flex items-center gap-1"><User size={11} />{b.teamName}</span>
                        <span className="flex items-center gap-1"><Tag size={11} />{b.jiraTicket}</span>
                        <span className="flex items-center gap-1"><FileText size={11} />{b.purpose}</span>
                        <span className="flex items-center gap-1"><Clock size={11} />Release: {new Date(b.releaseAt).toLocaleString()}</span>
                      </div>
                    </div>
                    <div className="text-xs text-slate-500">{b.bookedBy}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default EnvironmentBookingsPage
