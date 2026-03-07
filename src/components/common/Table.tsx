interface Column {
    header: string
    accessor: string
  }
  
  interface TableProps {
    columns: Column[]
    data: any[]
  }
  
  const Table = ({ columns, data }: TableProps) => {
    return (
      <table className="w-full text-left">
        <thead>
          <tr className="text-slate-400 border-b border-slate-700">
            {columns.map((col) => (
              <th key={col.accessor} className="py-2">
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
  
        <tbody>
          {data.map((row, i) => (
            <tr key={i} className="border-b border-slate-800">
              {columns.map((col) => (
                <td key={col.accessor} className="py-3">
                  {row[col.accessor]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    )
  }
  
  export default Table