import type { ReactNode } from 'react';
import { Modal } from './Modal';

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'default' | 'danger';
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

const TITLE_ID = 'confirm-dialog-title';

/** Кастомный диалог подтверждения — замена нативного window.confirm(). */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  cancelLabel = 'Отмена',
  variant = 'default',
  busy = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const resolvedConfirmLabel = confirmLabel ?? (variant === 'danger' ? 'Удалить' : 'Подтвердить');

  return (
    <Modal
      open={open}
      onClose={onCancel}
      titleId={TITLE_ID}
      size="confirm"
      closeOnBackdrop
      closeOnEscape
      footer={
        <div className="modal-actions modal-actions--center">
          <button type="button" className="btn-ghost" onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={variant === 'danger' ? 'btn-danger' : 'btn-primary'}
            onClick={onConfirm}
            disabled={busy}
          >
            {busy ? 'Выполняется…' : resolvedConfirmLabel}
          </button>
        </div>
      }
    >
      <h3 id={TITLE_ID}>{title}</h3>
      {message && <p className="modal-hint">{message}</p>}
    </Modal>
  );
}
