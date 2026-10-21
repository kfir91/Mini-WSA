type Props = {
  tone: 'info' | 'error';
  children: React.ReactNode;
};

export function StatusBanner({ tone, children }: Props) {
  return (
    <div className={`banner show ${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}
