import { createContext, useContext, useState, useEffect, ReactNode } from "react"

export type AppRole = "DEVELOPER" | "PROJECT_MANAGER"

interface RoleContextValue {
  role: AppRole
  setRole: (role: AppRole) => void
  isProjectManager: boolean
}

const STORAGE_KEY = "openshift_monitor_role"

const RoleContext = createContext<RoleContextValue>({
  role: "DEVELOPER",
  setRole: () => {},
  isProjectManager: false,
})

export const RoleProvider = ({ children }: { children: ReactNode }) => {
  const [role, setRoleState] = useState<AppRole>(() => {
    const stored = localStorage.getItem(STORAGE_KEY)
    return (stored === "PROJECT_MANAGER" ? "PROJECT_MANAGER" : "DEVELOPER") as AppRole
  })

  const setRole = (newRole: AppRole) => {
    localStorage.setItem(STORAGE_KEY, newRole)
    setRoleState(newRole)
  }

  return (
    <RoleContext.Provider value={{ role, setRole, isProjectManager: role === "PROJECT_MANAGER" }}>
      {children}
    </RoleContext.Provider>
  )
}

export const useRole = () => useContext(RoleContext)
