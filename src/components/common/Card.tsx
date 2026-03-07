import React from "react"

interface CardProps {
  title?: string
  children: React.ReactNode
}

const Card: React.FC<CardProps> = ({ title, children }) => {
  return (
    <div className="bg-gradient-to-b from-slate-800 to-slate-900 border border-slate-700 rounded-xl p-8">
      {title && (
        <h3 className="text-lg font-semibold mb-4 text-slate-200">
          {title}
        </h3>
      )}
      {children}
    </div>
  )
}

export default Card