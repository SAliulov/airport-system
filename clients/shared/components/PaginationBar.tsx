interface PaginationBarProps {
  page: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  /** CSS class for prev/next buttons (e.g. `btn-ghost btn-sm` or `preset-btn`). */
  buttonClassName?: string;
}

export function PaginationBar({
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  buttonClassName = 'btn-ghost btn-sm',
}: PaginationBarProps) {
  if (totalElements === 0) return null;

  const from = page * pageSize + 1;
  const to = Math.min((page + 1) * pageSize, totalElements);

  return (
    <div className="pagination-bar">
      <span className="pagination-bar__info">
        Показано {from}–{to} из {totalElements}
      </span>
      <div className="pagination-bar__controls">
        <button
          type="button"
          className={buttonClassName}
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          ←
        </button>
        <span className="pagination-bar__page">
          {page + 1} / {Math.max(totalPages, 1)}
        </span>
        <button
          type="button"
          className={buttonClassName}
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          →
        </button>
      </div>
    </div>
  );
}

export default PaginationBar;
