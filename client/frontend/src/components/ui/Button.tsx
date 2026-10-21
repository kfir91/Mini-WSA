type Props = {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  variant?: 'default' | 'primary';
  type?: 'button' | 'submit';
};

export function Button({
  children,
  onClick,
  disabled,
  variant = 'default',
  type = 'button',
}: Props) {
  return (
    <button
      type={type}
      className={`btn${variant === 'primary' ? ' btn--primary' : ''}`}
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  );
}
