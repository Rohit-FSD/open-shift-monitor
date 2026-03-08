const Loader = ({ text = "Loading..." }) => {

    return (
  
      <div className="flex flex-col items-center justify-center py-10 text-slate-400">
  
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-slate-300 mb-3"></div>
  
        <p className="text-sm">{text}</p>
  
      </div>
  
    )
  }
  
  export default Loader