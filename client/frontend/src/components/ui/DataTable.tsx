type Column<T> = {
  key: string;
  header: string;
  numeric?: boolean;
  cell: (row: T) => React.ReactNode;
};

type Props<T> = {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  emptyLabel: string;
};

export function DataTable<T>({ columns, rows, rowKey, emptyLabel }: Props<T>) {
  if (rows.length === 0) {
    return <p className="empty">{emptyLabel}</p>;
  }

  return (
    <table className="data-table">
      <thead>
        <tr>
          {columns.map((col) => (
            <th key={col.key}>{col.header}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={rowKey(row)}>
            {columns.map((col) => (
              <td key={col.key} className={col.numeric ? 'num' : undefined}>
                {col.cell(row)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
