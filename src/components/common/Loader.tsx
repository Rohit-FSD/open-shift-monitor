interface Props {
    text?: string
  }
  
  const Loader = ({ text }: Props) => {
  
    return (
  
      <div className="flex flex-col items-center justify-center py-8 text-slate-400">
  
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-400 mb-3" />
  
        <div className="text-sm">
          {text || "Loading..."}
        </div>
  
      </div>
  
    )
  
  }
  
  export default Loader