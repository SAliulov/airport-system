import { useEffect, type ReactNode } from 'react';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  titleId: string;
  /** Некликающийся контент над скроллящейся областью (заголовок, ошибка, подсказка). */
  header?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'default' | 'edit' | 'form' | 'confirm';
  /** По умолчанию false — клик мимо не должен молча закрывать форму с несохранёнными данными. */
  closeOnBackdrop?: boolean;
  closeOnEscape?: boolean;
}

/** Общий примитив модалки: overlay + карточка со скроллящимся телом и опциональными header/footer. */
export function Modal({
  open,
  onClose,
  titleId,
  header,
  children,
  footer,
  size = 'default',
  closeOnBackdrop = false,
  closeOnEscape = closeOnBackdrop,
}: ModalProps) {
  useEffect(() => {
    if (!open || !closeOnEscape) return;
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [open, closeOnEscape, onClose]);

  if (!open) return null;

  const sizeClass = size === 'default' ? '' : ` modal-card--${size}`;

  return (
    <div
      className="modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      onClick={closeOnBackdrop ? e => { if (e.target === e.currentTarget) onClose(); } : undefined}
    >
      <div className={`modal-card${sizeClass}`} onClick={e => e.stopPropagation()}>
        {header}
        <div className="modal-card__body">{children}</div>
        {footer}
      </div>
    </div>
  );
}
