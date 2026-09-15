import type { FormEvent, ReactNode } from "react";

type Props = {
  title: string;
  body: ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

export function ConfirmDialog({ title, body, confirmLabel = "确认", danger, onClose, onConfirm }: Props) {
  function submit(event: FormEvent) {
    event.preventDefault();
    onConfirm();
  }

  return (
    <div className="modal-back" onClick={onClose} role="presentation">
      <form
        className="modal"
        onClick={(event) => event.stopPropagation()}
        onSubmit={submit}
      >
        <h3>{title}</h3>
        <div>{body}</div>
        <div className="row" style={{ marginTop: 16, justifyContent: "flex-end" }}>
          <button type="button" className="btn ghost" onClick={onClose}>
            取消
          </button>
          <button type="submit" className={`btn ${danger ? "danger" : "primary"}`}>
            {confirmLabel}
          </button>
        </div>
      </form>
    </div>
  );
}
